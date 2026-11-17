"""Descriptive feasibility of predicting an athlete's outdoor 60 m season best."""

from dataclasses import dataclass
from datetime import date

import pandas as pd

from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class SeasonFeasibility:
    seasons: pd.DataFrame
    test_coverage: pd.DataFrame
    input_ages: pd.DataFrame
    distinct_eligible_athletes: int


def select_best_latest_attempt(
    results: pd.DataFrame, higher_is_better: bool
) -> pd.Series:
    """Choose the best attempt on the latest date; resolve equal values by result ID."""
    latest = results.loc[results["result_date"] == results["result_date"].max()].copy()
    latest["value"] = pd.to_numeric(latest["value"], errors="raise")
    if latest["value"].isna().any():
        raise ValueError("A test attempt has no numeric value.")
    return latest.sort_values(
        ["value", "id"], ascending=[not higher_is_better, True]
    ).iloc[0]


def assess_seasons(
    data: AuditData,
    competition: CompetitionData,
    event_id: int,
    as_of: date,
    confirmed_outdoor_ids: frozenset[int] = frozenset(),
) -> SeasonFeasibility:
    """Assess candidate athlete-seasons without exporting labels or individual records."""
    if as_of > date.today():
        raise ValueError("The audit date cannot be in the future.")
    event = competition.events.loc[competition.events["id"] == event_id]
    if len(event) != 1 or str(event.iloc[0]["name"]).strip().casefold() != "60 m":
        raise ValueError("The chosen event ID must identify the verified '60 m' event.")
    if event.iloc[0]["unit"] != "s":
        raise ValueError("The selected 60 m event does not use seconds; review its metadata.")

    tests = data.results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    results = competition.results.loc[competition.results["event_id"] == event_id].copy()
    results["result_date"] = pd.to_datetime(results["result_date"])
    results = results.loc[results["result_date"] <= pd.Timestamp(as_of)]
    if results.empty:
        raise ValueError("No 60 m competition results exist on or before the audit date.")
    if confirmed_outdoor_ids:
        confirmed = results.loc[results["id"].isin(confirmed_outdoor_ids)]
        if (
            set(confirmed["id"]) != confirmed_outdoor_ids
            or not confirmed["season_type"].eq("UNKNOWN").all()
            or not confirmed["result_date"].between(
                pd.Timestamp("2026-05-01"), pd.Timestamp("2026-10-31")
            ).all()
        ):
            raise ValueError(
                "Confirmed outdoor IDs must identify UNKNOWN results for the selected "
                "60 m event in May–October 2026, present on the audit date."
            )
    results["numeric_value"] = pd.to_numeric(results["numeric_value"], errors="coerce")

    first_year = int(results["result_date"].min().year)
    test_metadata = data.tests.set_index("id")
    season_rows: list[dict[str, object]] = []
    feature_rows: list[dict[str, object]] = []
    age_rows: list[dict[str, object]] = []
    all_eligible_athletes: set[object] = set()
    for year in range(first_year, as_of.year + 1):
        start = pd.Timestamp(year=year, month=5, day=1)
        end = pd.Timestamp(year=year, month=10, day=31)
        observed_end = min(end, pd.Timestamp(as_of))
        closed = as_of > end.date()
        season = results.loc[
            (results["result_date"] >= start) & (results["result_date"] <= observed_end)
        ]
        unknown = season.loc[season["season_type"] == "UNKNOWN"]
        recorded_outdoor = season.loc[season["season_type"] == "OUTDOOR"]
        confirmed_unknown = unknown.loc[unknown["id"].isin(confirmed_outdoor_ids)]
        outdoor = season.loc[
            (season["season_type"] == "OUTDOOR") | season["id"].isin(confirmed_outdoor_ids)
        ]
        good = outdoor["numeric_value"].notna() & (outdoor["numeric_value"] > 0) & (outdoor["unit"] == "s")
        # A nonnumeric or differently unitized outdoor mark could hide the season best.
        bad_athletes = set(outdoor.loc[~good, "athlete_id"])
        targets = outdoor.loc[good].groupby("athlete_id")["numeric_value"].min()
        feature_rows_for_year: list[dict[str, object]] = []
        with_test = 0
        prior_with_no_outdoor = 0
        prior_with_review = 0
        prior_with_unknown = 0
        eligible_athletes: set[object] = set()
        unknown_numeric_athletes = set(unknown.loc[
            unknown["numeric_value"].notna()
            & (unknown["numeric_value"] > 0) & (unknown["unit"] == "s"), "athlete_id"
        ])
        prior_with_numeric_unknown = 0
        for athlete_id in data.athletes["id"]:
            history = tests.loc[
                (tests["athlete_id"] == athlete_id) & (tests["result_date"] < start)
                & (tests["result_date"] <= pd.Timestamp(as_of))
            ]
            if history.empty:
                continue
            with_test += 1
            prediction_date = history["result_date"].max()
            prior_with_unknown += int(athlete_id in set(unknown["athlete_id"]))
            prior_with_numeric_unknown += int(athlete_id in unknown_numeric_athletes)
            prior_with_review += int(athlete_id in bad_athletes)
            prior_with_no_outdoor += int(athlete_id not in set(outdoor["athlete_id"]))
            eligible = closed and athlete_id in targets.index and athlete_id not in bad_athletes
            if eligible:
                eligible_athletes.add(athlete_id)
                all_eligible_athletes.add(athlete_id)
            age_rows.append({
                "season_year": year,
                "cohort": "all_with_prior_test",
                "prediction_date_age_days": (start - prediction_date).days,
            })
            if eligible:
                age_rows.append({
                    "season_year": year,
                    "cohort": "eligible",
                    "prediction_date_age_days": (start - prediction_date).days,
                })
            for test_id, group in history.groupby("test_id"):
                if test_id not in test_metadata.index:
                    raise ValueError(f"Result references unknown test ID {test_id}.")
                chosen = select_best_latest_attempt(group, bool(test_metadata.loc[test_id, "higher_is_better"]))
                latest_date = chosen["result_date"]
                attempts = group.loc[group["result_date"] == latest_date]
                # The selected value is deliberately not exported; this is a feasibility audit.
                feature_rows_for_year.append({
                    "test_id": test_id, "eligible": eligible,
                    "age_days": (prediction_date - latest_date).days,
                    "same_day_attempts": len(attempts),
                })
        season_rows.append({
            "season_year": year,
            "season_complete": closed,
            "athletes_in_database": len(data.athletes),
            "athletes_with_prior_test": with_test,
            "athletes_with_outdoor_60m": outdoor["athlete_id"].nunique(),
            "athletes_with_recorded_outdoor_60m": recorded_outdoor["athlete_id"].nunique(),
            "confirmed_unknown_as_outdoor_result_count": len(confirmed_unknown),
            "athletes_with_unknown_season_60m": unknown["athlete_id"].nunique(),
            "unknown_season_result_count": len(unknown),
            "outdoor_records_without_comparable_numeric_time": int((~good).sum()),
            "athletes_with_outdoor_records_requiring_review": len(bad_athletes),
            "prior_test_athletes_without_outdoor_60m": prior_with_no_outdoor,
            "prior_test_athletes_with_unknown_season_60m": prior_with_unknown,
            "prior_test_athletes_with_numeric_unknown_60m": prior_with_numeric_unknown,
            "prior_test_athletes_with_outdoor_records_requiring_review": prior_with_review,
            "eligible_athlete_seasons": len(eligible_athletes),
        })
        for cohort, denominator in (
            ("all_with_prior_test", with_test), ("eligible", len(eligible_athletes))
        ):
            for metadata in data.tests.to_dict("records"):
                test_id = metadata["id"]
                matching = [
                    row for row in feature_rows_for_year
                    if row["test_id"] == test_id and (cohort == "all_with_prior_test" or row["eligible"])
                ]
                ages = [row["age_days"] for row in matching]
                feature_rows.append({
                    "season_year": year, "cohort": cohort, "test_id": test_id,
                    "test_name": metadata["name"], "unit": metadata["unit"],
                    "higher_is_better": metadata["higher_is_better"],
                    "cohort_athlete_seasons": denominator,
                    "with_result": len(matching),
                    "missing_result": denominator - len(matching),
                    "median_age_days": pd.Series(ages, dtype="float64").median(),
                    "maximum_age_days": max(ages) if ages else None,
                    "multiple_attempts_same_day": sum(row["same_day_attempts"] > 1 for row in matching),
                })

    season_frame = pd.DataFrame(season_rows)
    coverage = pd.DataFrame(feature_rows, columns=[
        "season_year", "cohort", "test_id", "test_name", "unit", "higher_is_better",
        "cohort_athlete_seasons", "with_result", "missing_result", "median_age_days",
        "maximum_age_days", "multiple_attempts_same_day",
    ])
    ages = pd.DataFrame(age_rows, columns=["season_year", "cohort", "prediction_date_age_days"])
    if ages.empty:
        age_summary = pd.DataFrame(columns=["season_year", "cohort", "minimum_days", "median_days", "maximum_days"])
    else:
        age_summary = ages.groupby(["season_year", "cohort"]).agg(
            minimum_days=("prediction_date_age_days", "min"),
            median_days=("prediction_date_age_days", "median"),
            maximum_days=("prediction_date_age_days", "max"),
        ).reset_index()
    return SeasonFeasibility(season_frame, coverage, age_summary, len(all_eligible_athletes))
