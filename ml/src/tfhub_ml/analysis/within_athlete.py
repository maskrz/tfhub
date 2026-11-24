"""Explore repeated tests against subsequent 100 m outcomes without fitting a model."""

from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
import pandas as pd

from tfhub_ml.analysis.near_term_100m import build_examples
from tfhub_ml.analysis.season_feasibility import select_best_latest_attempt
from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class WithinAthleteAudit:
    coverage: pd.DataFrame
    correlations: pd.DataFrame


def build_season_examples(
    examples: pd.DataFrame, competition: CompetitionData, event_id: int,
    as_of: date, confirmed_ids: frozenset[int],
) -> pd.DataFrame:
    """Build full-season recorded-best outcomes for calendar-year prediction dates."""
    races = competition.results.loc[competition.results["event_id"] == event_id].copy()
    races["result_date"] = pd.to_datetime(races["result_date"])
    races = races.loc[races["result_date"] <= pd.Timestamp(as_of)].copy()
    races["numeric_value"] = pd.to_numeric(races["numeric_value"], errors="coerce")
    races["outdoor"] = races["season_type"].eq("OUTDOOR") | races["id"].isin(confirmed_ids)
    races["valid"] = races["numeric_value"].notna() & np.isfinite(races["numeric_value"]) & (races["numeric_value"] > 0) & races["unit"].eq("s")
    rows = []
    for row in examples.itertuples(index=False):
        prediction = row.prediction_date
        start = pd.Timestamp(year=prediction.year, month=5, day=1)
        end = pd.Timestamp(year=prediction.year, month=10, day=31)
        if prediction >= end or pd.Timestamp(as_of) <= end:
            continue
        season = races.loc[
            (races["athlete_id"] == row.athlete_id) & races["result_date"].between(start, end)
        ]
        unknown = season["season_type"].eq("UNKNOWN") & ~season["id"].isin(confirmed_ids)
        bad = season["outdoor"] & ~season["valid"]
        # Ordering of a race on the prediction date is unknown.
        same_day = season["outdoor"] & season["result_date"].eq(prediction)
        eligible = season.loc[season["outdoor"] & season["valid"]]
        status = "review" if (unknown | bad | same_day).any() else ("observed" if not eligible.empty else "no_observed_race")
        known = eligible.loc[eligible["result_date"] < prediction]
        best = eligible.sort_values(["numeric_value", "result_date", "id"]).iloc[0] if status == "observed" else None
        rows.append({
            "athlete_id": row.athlete_id, "prediction_date": prediction, "window_end": end,
            "period": "preseason" if prediction < start else "in_season",
            "status": status, "target": best["numeric_value"] if best is not None else np.nan,
            "target_result_id": best["id"] if best is not None else pd.NA,
            "known_season_best": known["numeric_value"].min() if not known.empty else np.nan,
            "future_race_rows": len(eligible.loc[eligible["result_date"] > prediction]),
        })
    columns = ["athlete_id", "prediction_date", "window_end", "period", "status", "target", "target_result_id", "known_season_best", "future_race_rows"]
    result = pd.DataFrame(rows, columns=columns)
    result["prediction_date"] = pd.to_datetime(result["prediction_date"])
    result["window_end"] = pd.to_datetime(result["window_end"])
    return result


def selected_test_anchors(examples: pd.DataFrame, test_results: pd.DataFrame, higher: bool) -> pd.DataFrame:
    """Retain anchors immediately after a test date, not later repeated reuse of that test."""
    if examples.empty or test_results.empty:
        return examples.iloc[0:0].assign(test_date=pd.NaT, test_value=np.nan, test_result_id=pd.NA)
    tests = test_results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    tests["value"] = pd.to_numeric(tests["value"], errors="coerce")
    chosen = []
    for (_, _), attempts in tests.groupby(["athlete_id", "result_date"]):
        values = attempts["value"]
        if values.isna().any() or not np.isfinite(values).all() or (values <= 0).any():
            continue
        chosen.append(select_best_latest_attempt(attempts, higher))
    if not chosen:
        return examples.iloc[0:0].assign(test_date=pd.NaT, test_value=np.nan, test_result_id=pd.NA)
    tests = pd.DataFrame(chosen).rename(columns={"id": "test_result_id", "result_date": "test_date", "value": "test_value"})
    tests["test_date"] = pd.to_datetime(tests["test_date"])
    tests["prediction_date"] = pd.to_datetime(
        [timestamp.date() + timedelta(days=1) for timestamp in tests["test_date"]]
    )
    return examples.merge(
        tests[["athlete_id", "prediction_date", "test_date", "test_value", "test_result_id"]],
        on=["athlete_id", "prediction_date"], how="inner", validate="one_to_one",
    )


def build_changes(observed: pd.DataFrame) -> pd.DataFrame:
    """Pair each eligible test with its latest previous fully matured, nonoverlapping outcome."""
    changes = []
    for athlete_id, group in observed.groupby("athlete_id"):
        ordered = group.sort_values("test_date")
        for current in ordered.itertuples(index=False):
            eligible = ordered.loc[ordered["window_end"] < current.test_date]
            if eligible.empty:
                continue
            previous = eligible.sort_values("test_date").iloc[-1]
            changes.append({
                "athlete_id": athlete_id,
                "test_change": current.test_value - previous["test_value"],
                "time_change": current.target - previous["target"],
                "prior_test_result_id": previous["test_result_id"],
                "current_test_result_id": current.test_result_id,
                "prior_target_id": previous["target_result_id"],
                "current_target_id": current.target_result_id,
                "gap_days": (current.test_date - previous["test_date"]).days,
            })
    return pd.DataFrame(changes, columns=[
        "athlete_id", "test_change", "time_change", "prior_test_result_id", "current_test_result_id",
        "prior_target_id", "current_target_id", "gap_days",
    ])


def coefficient(x: pd.Series, y: pd.Series, method: str) -> float | None:
    if len(x) < 2 or x.nunique() < 2 or y.nunique() < 2:
        return None
    if method == "spearman":
        x, y = x.rank(method="average"), y.rank(method="average")
    return float(x.corr(y))


def analyze(data: AuditData, competition: CompetitionData, event_id: int,
            as_of: date, confirmed_ids: frozenset[int], horizon: int) -> WithinAthleteAudit:
    # Build anchors with horizon 1 so full-season analysis does not lose late-season test dates.
    anchor_examples = build_examples(data, competition, event_id, None, as_of, confirmed_ids, 1)
    season = build_season_examples(anchor_examples, competition, event_id, as_of, confirmed_ids)
    near = build_examples(data, competition, event_id, None, as_of, confirmed_ids, horizon)
    near["period"] = "all"
    coverage, correlations = [], []
    for test in data.tests.sort_values("id").to_dict("records"):
        if pd.isna(test["higher_is_better"]):
            raise ValueError(f"Missing direction for test ID {test['id']}.")
        source = data.results.loc[data.results["test_id"] == test["id"]]
        for outcome, examples in (("next_30_days", near), ("season_best", season)):
            anchors = selected_test_anchors(examples, source, bool(test["higher_is_better"]))
            observed = anchors.loc[anchors["status"] == "observed"].copy()
            coverage.append({
                "outcome": outcome, "test_id": test["id"], "test_name": test["name"], "unit": test["unit"],
                "anchor_dates": len(anchors), "athletes": anchors["athlete_id"].nunique(),
                "observed_dates": len(observed), "observed_athletes": observed["athlete_id"].nunique(),
                "no_recorded_outcome": int((anchors["status"] == "no_observed_race").sum()),
                "needs_review": int((anchors["status"] == "review").sum()),
                "preseason_observed": int((observed["period"] == "preseason").sum()),
                "in_season_observed": int((observed["period"] == "in_season").sum()),
                "known_best_available": int(observed["known_season_best"].notna().sum()) if outcome == "season_best" else 0,
                "observed_without_future_race": int((observed["future_race_rows"] == 0).sum()) if outcome == "season_best" else 0,
            })
            groups = {"all_test_dates": observed}
            if outcome == "season_best":
                for period in ("preseason", "in_season"):
                    subset = observed.loc[observed["period"] == period].copy()
                    subset["season_year"] = subset["prediction_date"].dt.year
                    subset = subset.sort_values("prediction_date")
                    groups[period + "_earliest"] = subset.drop_duplicates(["athlete_id", "season_year"], keep="first")
                    groups[period + "_latest"] = subset.drop_duplicates(["athlete_id", "season_year"], keep="last")
            for name, group in groups.items():
                for method in ("pearson", "spearman"):
                    correlations.append({
                        "outcome": outcome, "analysis": name, "test_id": test["id"], "test_name": test["name"],
                        "unit": test["unit"], "higher_is_better": test["higher_is_better"], "method": method,
                        "paired_observations": len(group), "athletes": group["athlete_id"].nunique(),
                        "distinct_test_results": group["test_result_id"].nunique(), "distinct_target_races": group["target_result_id"].nunique(),
                        "median_gap_days": None, "correlation": coefficient(group["test_value"], group["target"], method),
                    })
            changes = build_changes(observed)
            for method in ("pearson", "spearman"):
                correlations.append({
                    "outcome": outcome, "analysis": "within_athlete_change", "test_id": test["id"], "test_name": test["name"],
                    "unit": test["unit"], "higher_is_better": test["higher_is_better"], "method": method,
                    "paired_observations": len(changes), "athletes": changes["athlete_id"].nunique(),
                    "distinct_test_results": pd.concat([changes["prior_test_result_id"], changes["current_test_result_id"]]).nunique(),
                    "distinct_target_races": pd.concat([changes["prior_target_id"], changes["current_target_id"]]).nunique(),
                    "median_gap_days": changes["gap_days"].median() if not changes.empty else None,
                    "correlation": coefficient(changes["test_change"], changes["time_change"], method),
                })
    return WithinAthleteAudit(pd.DataFrame(coverage), pd.DataFrame(correlations))
