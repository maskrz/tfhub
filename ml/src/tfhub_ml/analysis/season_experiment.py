"""Exploratory one-feature, leave-one-athlete-out season experiment."""

from dataclasses import dataclass
from datetime import date

import numpy as np
import pandas as pd

from tfhub_ml.analysis.season_feasibility import assess_seasons, select_best_latest_attempt
from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class ExperimentResult:
    metrics: pd.DataFrame
    athlete_count: int
    candidate_count: int
    missing_input_count: int
    unconfirmed_unknown_athletes: int


def build_examples(
    data: AuditData,
    competition: CompetitionData,
    event_id: int,
    test_id: int,
    year: int,
    as_of: date,
    confirmed_ids: frozenset[int],
) -> tuple[pd.DataFrame, int]:
    """Construct transient athlete-level examples; never export this dataframe."""
    audit = assess_seasons(data, competition, event_id, as_of, confirmed_ids)
    if as_of <= date(year, 10, 31):
        raise ValueError("The chosen season must be complete before the audit date.")
    test = data.tests.loc[data.tests["id"] == test_id]
    if (
        len(test) != 1
        or str(test.iloc[0]["name"]).strip().casefold() != "60m w kolcach"
        or test.iloc[0]["unit"] != "s"
        or bool(test.iloc[0]["higher_is_better"])
    ):
        raise ValueError("Selected test must be the verified 60m w kolcach time in seconds, with lower better.")

    start = pd.Timestamp(year=year, month=5, day=1)
    end = pd.Timestamp(year=year, month=10, day=31)
    tests = data.results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    competition_results = competition.results.copy()
    competition_results["result_date"] = pd.to_datetime(competition_results["result_date"])
    season = competition_results.loc[
        (competition_results["event_id"] == event_id)
        & competition_results["result_date"].between(start, end)
        & (competition_results["result_date"] <= pd.Timestamp(as_of))
    ].copy()
    outdoor = season.loc[
        season["season_type"].eq("OUTDOOR") | season["id"].isin(confirmed_ids)
    ].copy()
    outdoor["numeric_value"] = pd.to_numeric(outdoor["numeric_value"], errors="coerce")
    valid = outdoor["numeric_value"].notna() & (outdoor["numeric_value"] > 0) & outdoor["unit"].eq("s")
    bad_athletes = set(outdoor.loc[~valid, "athlete_id"])
    targets = outdoor.loc[valid].groupby("athlete_id")["numeric_value"].min()
    examples: list[dict[str, object]] = []
    for athlete_id in data.athletes["id"]:
        history = tests.loc[
            (tests["athlete_id"] == athlete_id)
            & (tests["result_date"] < start)
            & (tests["result_date"] <= pd.Timestamp(as_of))
        ]
        if history.empty or athlete_id not in targets.index or athlete_id in bad_athletes:
            continue
        input_history = history.loc[history["test_id"] == test_id]
        if input_history.empty:
            continue
        choice = select_best_latest_attempt(input_history, False)
        value = float(choice["value"])
        if not np.isfinite(value) or value <= 0:
            raise ValueError("Selected input time must be finite and positive.")
        examples.append({"athlete_id": athlete_id, "input_time": value, "target_time": float(targets.loc[athlete_id])})
    candidate = audit.seasons.loc[audit.seasons["season_year"] == year, "eligible_athlete_seasons"]
    if len(candidate) != 1:
        raise ValueError("Chosen season is outside the feasibility audit's observed date range.")
    rows = pd.DataFrame(examples, columns=["athlete_id", "input_time", "target_time"])
    unresolved = season.loc[
        season["season_type"].eq("UNKNOWN") & ~season["id"].isin(confirmed_ids)
        & season["athlete_id"].isin(rows["athlete_id"])
    ]["athlete_id"].nunique()
    return rows, int(unresolved)


def evaluate_examples(examples: pd.DataFrame) -> pd.DataFrame:
    """Compare methods using independently held-out athletes."""
    if len(examples) != examples["athlete_id"].nunique():
        raise ValueError("The study requires one example per athlete in a single season.")
    if len(examples) < 3:
        raise ValueError("At least three distinct athletes are needed for leave-one-athlete-out evaluation.")
    if not np.isfinite(examples[["input_time", "target_time"]].to_numpy(dtype=float)).all():
        raise ValueError("Input and target times must be finite.")
    residuals: dict[str, list[float]] = {"mean_baseline": [], "linear_one_feature": []}
    for athlete_id in examples["athlete_id"]:
        training = examples.loc[examples["athlete_id"] != athlete_id]
        held_out = examples.loc[examples["athlete_id"] == athlete_id].iloc[0]
        x = training["input_time"].to_numpy(dtype=float)
        y = training["target_time"].to_numpy(dtype=float)
        if np.ptp(x) == 0:
            raise ValueError("A training fold has constant input times; linear regression is undefined.")
        x_mean, y_mean = x.mean(), y.mean()
        slope = np.sum((x - x_mean) * (y - y_mean)) / np.sum((x - x_mean) ** 2)
        linear = y_mean + slope * (held_out["input_time"] - x_mean)
        residuals["mean_baseline"].append(y_mean - held_out["target_time"])
        residuals["linear_one_feature"].append(linear - held_out["target_time"])
    metrics = []
    for method, errors in residuals.items():
        errors_array = np.asarray(errors, dtype=float)
        metrics.append({
            "method": method,
            "held_out_athletes": len(errors),
            "mean_absolute_error_seconds": np.mean(np.abs(errors_array)),
            "median_absolute_error_seconds": np.median(np.abs(errors_array)),
            "maximum_absolute_error_seconds": np.max(np.abs(errors_array)),
            "root_mean_squared_error_seconds": np.sqrt(np.mean(errors_array ** 2)),
            "mean_signed_error_seconds": np.mean(errors_array),
        })
    return pd.DataFrame(metrics)


def run_experiment(
    data: AuditData, competition: CompetitionData, event_id: int, test_id: int,
    year: int, as_of: date, confirmed_ids: frozenset[int],
) -> ExperimentResult:
    examples, unresolved = build_examples(data, competition, event_id, test_id, year, as_of, confirmed_ids)
    audit = assess_seasons(data, competition, event_id, as_of, confirmed_ids)
    candidate_count = int(audit.seasons.loc[audit.seasons["season_year"] == year, "eligible_athlete_seasons"].iloc[0])
    if len(examples) > candidate_count:
        raise ValueError("Experiment cohort exceeds the feasibility cohort; review eligibility rules.")
    return ExperimentResult(
        evaluate_examples(examples), len(examples), candidate_count,
        candidate_count - len(examples), unresolved,
    )
