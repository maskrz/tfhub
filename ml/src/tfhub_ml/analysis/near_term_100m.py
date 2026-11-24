"""Retrospective, date-aware 100 m experiment."""

from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
import pandas as pd

from tfhub_ml.analysis.sprint_readiness import assess_sprint_readiness
from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class NearTermStudy:
    counts: pd.DataFrame
    years: pd.DataFrame
    metrics: pd.DataFrame


def build_examples(data: AuditData, competition: CompetitionData, event_id: int, test_id: int | None,
                   as_of: date, confirmed_ids: frozenset[int], horizon: int) -> pd.DataFrame:
    original = competition.results.loc[
        (competition.results["event_id"] == event_id)
        & (pd.to_datetime(competition.results["result_date"]) <= pd.Timestamp(as_of))
        & competition.results["id"].isin(confirmed_ids)
    ]
    if set(original["id"]) != confirmed_ids or not original["season_type"].isin(["UNKNOWN", "OUTDOOR"]).all():
        raise ValueError("Confirmed IDs must identify UNKNOWN or now-OUTDOOR results for the selected 100 m event on or before the cutoff.")
    still_unknown = frozenset(original.loc[original["season_type"] == "UNKNOWN", "id"])
    assess_sprint_readiness(data, competition, event_id, as_of, (horizon,), still_unknown)
    if test_id is not None:
        test = data.tests.loc[data.tests["id"] == test_id]
        if (len(test) != 1 or str(test.iloc[0]["name"]).strip().casefold() != "60m w kolcach"
                or test.iloc[0]["unit"] != "s" or bool(test.iloc[0]["higher_is_better"])):
            raise ValueError("Selected test must be the verified 60m w kolcach test in seconds, lower is better.")
    cutoff = pd.Timestamp(as_of)
    races = competition.results.loc[competition.results["event_id"] == event_id].copy()
    races["result_date"] = pd.to_datetime(races["result_date"])
    races = races.loc[races["result_date"] <= cutoff].copy()
    races["numeric_value"] = pd.to_numeric(races["numeric_value"], errors="coerce")
    races["comparable"] = races["numeric_value"].notna() & np.isfinite(races["numeric_value"]) & (races["numeric_value"] > 0) & races["unit"].eq("s")
    races["outdoor"] = races["season_type"].eq("OUTDOOR") | races["id"].isin(confirmed_ids)
    tests = data.results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    tests = tests.loc[tests["result_date"] <= cutoff].copy()
    tests["value"] = pd.to_numeric(tests["value"], errors="coerce")
    date_sources = [frame[["athlete_id", "result_date"]] for frame in (tests, races) if not frame.empty]
    anchor_dates = pd.concat(date_sources).drop_duplicates() if date_sources else pd.DataFrame(columns=["athlete_id", "result_date"])
    rows = []
    for anchor in anchor_dates.itertuples(index=False):
        prediction_day = pd.Timestamp(anchor.result_date).date() + timedelta(days=1)
        prediction_date = pd.Timestamp(prediction_day)
        end = pd.Timestamp(prediction_day + timedelta(days=horizon))
        if end > cutoff:
            continue
        athlete_races = races.loc[races["athlete_id"] == anchor.athlete_id]
        future = athlete_races.loc[(athlete_races["result_date"] > prediction_date) & (athlete_races["result_date"] <= end)]
        # An unresolved or noncomparable outdoor mark could be the true fastest race.
        unresolved = future["season_type"].eq("UNKNOWN") & ~future["id"].isin(confirmed_ids)
        invalid = future["outdoor"] & ~future["comparable"]
        usable = future.loc[future["outdoor"] & future["comparable"]]
        status = "review" if (unresolved | invalid).any() else ("observed" if not usable.empty else "no_observed_race")
        previous = athlete_races.loc[
            (athlete_races["result_date"] < prediction_date) & athlete_races["outdoor"] & athlete_races["comparable"]
        ]
        previous_time = np.nan
        previous_id, previous_date = pd.NA, None
        if not previous.empty:
            latest = previous["result_date"].max()
            chosen = previous.loc[previous["result_date"] == latest].sort_values(["numeric_value", "id"]).iloc[0]
            previous_time, previous_id, previous_date = chosen["numeric_value"], chosen["id"], latest
        target_id, target_date = pd.NA, None
        if status == "observed":
            chosen = usable.sort_values(["numeric_value", "result_date", "id"]).iloc[0]
            target_id, target_date = chosen["id"], chosen["result_date"]
        history = tests.loc[
            (tests["athlete_id"] == anchor.athlete_id) & (tests["test_id"] == test_id)
            & (tests["result_date"] < prediction_date)
        ]
        test_time = np.nan
        if not history.empty:
            latest_values = history.loc[history["result_date"] == history["result_date"].max(), "value"]
            if latest_values.notna().all() and np.isfinite(latest_values).all() and (latest_values > 0).all():
                test_time = latest_values.min()
        rows.append({
            "athlete_id": anchor.athlete_id, "prediction_date": prediction_date, "window_end": end,
            "status": status, "target": usable["numeric_value"].min() if status == "observed" else np.nan,
            "previous_time": previous_time, "test_time": test_time,
            "previous_result_id": previous_id, "previous_result_date": previous_date,
            "target_result_id": target_id, "target_result_date": target_date,
        })
    result = pd.DataFrame(rows, columns=[
        "athlete_id", "prediction_date", "window_end", "status", "target", "previous_time", "test_time",
        "previous_result_id", "previous_result_date", "target_result_id", "target_result_date",
    ])
    result["prediction_date"] = pd.to_datetime(result["prediction_date"])
    result["window_end"] = pd.to_datetime(result["window_end"])
    for column in ("previous_result_id", "target_result_id"):
        result[column] = pd.array(result[column], dtype="Int64")
    return result


def baseline_review(examples: pd.DataFrame) -> pd.DataFrame:
    """Prepare opt-in sensitive local review rows, ordered by largest baseline error."""
    review = examples.loc[
        (examples["status"] == "observed") & examples["previous_time"].notna(),
        ["athlete_id", "prediction_date", "window_end", "previous_result_id", "previous_result_date",
         "previous_time", "target_result_id", "target_result_date", "target"],
    ].copy()
    review["absolute_error_seconds"] = (review["previous_time"] - review["target"]).abs()
    return review.sort_values(
        ["absolute_error_seconds", "athlete_id", "prediction_date"],
        ascending=[False, True, True],
    ).reset_index(drop=True)


def _metrics(method: str, cohort: str, rows: pd.DataFrame, errors: list[float]) -> dict[str, object]:
    values = np.asarray(errors, dtype=float)
    per_athlete = pd.DataFrame({
        "athlete_id": rows["athlete_id"].to_numpy(), "absolute_error": np.abs(values),
    }).groupby("athlete_id")["absolute_error"].mean()
    return {
        "cohort": cohort, "method": method, "examples": len(values),
        "athletes": rows["athlete_id"].nunique(),
        "mae_seconds": np.mean(np.abs(values)),
        "athlete_weighted_mae_seconds": per_athlete.mean(),
        "median_absolute_error_seconds": np.median(np.abs(values)),
        "max_absolute_error_seconds": np.max(np.abs(values)),
    }


def run_study(data: AuditData, competition: CompetitionData, event_id: int, test_id: int,
              as_of: date, confirmed_ids: frozenset[int], horizon: int) -> NearTermStudy:
    examples = build_examples(data, competition, event_id, test_id, as_of, confirmed_ids, horizon)
    observed = examples.loc[examples["status"] == "observed"].copy()
    baseline = observed.loc[observed["previous_time"].notna()].copy()
    paired = observed.loc[observed["previous_time"].notna() & observed["test_time"].notna()].copy()
    counts = pd.DataFrame([{
        "complete_window_anchor_dates": len(examples),
        "anchor_athletes": examples["athlete_id"].nunique(),
        "observed_target_dates": len(observed), "observed_athletes": observed["athlete_id"].nunique(),
        "no_observed_race": int((examples["status"] == "no_observed_race").sum()),
        "target_requires_review": int((examples["status"] == "review").sum()),
        "with_previous_100m": len(baseline), "with_previous_and_test": len(paired),
        "athletes_with_previous_and_test": paired["athlete_id"].nunique(),
    }])
    years = examples.assign(year=examples["prediction_date"].dt.year).groupby("year").agg(
        anchor_dates=("athlete_id", "size"), athletes=("athlete_id", "nunique"),
        observed_targets=("status", lambda values: (values == "observed").sum()),
        no_observed_race=("status", lambda values: (values == "no_observed_race").sum()),
        requires_review=("status", lambda values: (values == "review").sum()),
    ).reset_index()
    metrics = []
    if not baseline.empty:
        metrics.append(_metrics("latest_100m", "all_with_previous", baseline, (baseline["previous_time"] - baseline["target"]).tolist()))
    if not paired.empty:
        metrics.append(_metrics("latest_100m", "with_previous_and_test", paired, (paired["previous_time"] - paired["target"]).tolist()))
    # Each fit excludes the athlete and every training label whose full horizon was not yet observed.
    evaluated, baseline_errors, model_errors = [], [], []
    for row in paired.itertuples():
        train = observed.loc[
            (observed["window_end"] < row.prediction_date)
            & (observed["athlete_id"] != row.athlete_id) & observed["test_time"].notna()
        ]
        if train["athlete_id"].nunique() < 3 or train["test_time"].nunique() < 2:
            continue
        x = train["test_time"].to_numpy(dtype=float)
        y = train["target"].to_numpy(dtype=float)
        slope = np.sum((x - x.mean()) * (y - y.mean())) / np.sum((x - x.mean()) ** 2)
        prediction = y.mean() + slope * (row.test_time - x.mean())
        evaluated.append(row.Index)
        baseline_errors.append(row.previous_time - row.target)
        model_errors.append(prediction - row.target)
    subset = paired.loc[evaluated]
    if not subset.empty:
        metrics.append(_metrics("latest_100m", "chronological_test_model_comparison", subset, baseline_errors))
        metrics.append(_metrics("linear_60m_test", "chronological_test_model_comparison", subset, model_errors))
    metric_columns = ["cohort", "method", "examples", "athletes", "mae_seconds", "athlete_weighted_mae_seconds", "median_absolute_error_seconds", "max_absolute_error_seconds"]
    return NearTermStudy(counts, years, pd.DataFrame(metrics, columns=metric_columns))
