"""Descriptive statistics and data-quality checks for audit snapshots."""

from __future__ import annotations

from datetime import date
from itertools import combinations

import numpy as np
import pandas as pd

from tfhub_ml.data.extract import AuditData

WINDOWS_DAYS = (7, 14, 30, 60)
KNOWN_UNITS = {"s", "m", "m/s"}
EXPECTED_UNITS_BY_CATEGORY = {"TIME": "s", "DISTANCE": "m", "SPEED": "m/s"}
KNOWN_CATEGORIES = set(EXPECTED_UNITS_BY_CATEGORY)
KNOWN_GENDERS = {"MALE", "FEMALE"}
KNOWN_RESULT_TYPES = {"TRAINING", "COMPETITION"}


def _as_date(value: object) -> str | None:
    return None if pd.isna(value) else pd.Timestamp(value).date().isoformat()


def overview(data: AuditData) -> dict[str, object]:
    athletes, tests, results = data.athletes, data.tests, data.results
    return {
        "athletes": len(athletes),
        "active_athletes": int(athletes["active"].fillna(False).sum()),
        "inactive_athletes": int((~athletes["active"].fillna(False)).sum()),
        "training_groups": data.training_group_count,
        "tests": len(tests),
        "active_tests": int(tests["active"].fillna(False).sum()),
        "test_results": len(results),
        "athletes_with_results": int(results["athlete_id"].nunique()),
        "tests_with_results": int(results["test_id"].nunique()),
        "earliest_result_date": _as_date(results["result_date"].min()),
        "latest_result_date": _as_date(results["result_date"].max()),
    }


def test_statistics(data: AuditData) -> pd.DataFrame:
    result_groups = {
        test_id: group for test_id, group in data.results.groupby("test_id", dropna=False)
    }
    records: list[dict[str, object]] = []
    for test in data.tests.to_dict("records"):
        group = result_groups.get(test["id"], data.results.iloc[0:0])
        values = pd.to_numeric(group["value"], errors="coerce")
        records.append(
            {
                "test_id": test["id"],
                "test_name": test["name"],
                "category": test["category"],
                "unit": test["unit"],
                "higher_is_better": test["higher_is_better"],
                "result_count": len(group),
                "athlete_count": int(group["athlete_id"].nunique()),
                "earliest_result_date": _as_date(group["result_date"].min()),
                "latest_result_date": _as_date(group["result_date"].max()),
                "mean": values.mean(),
                "median": values.median(),
                "minimum": values.min(),
                "maximum": values.max(),
                "standard_deviation": values.std(ddof=1) if values.count() > 1 else np.nan,
            }
        )
    columns = [
        "test_id", "test_name", "category", "unit", "higher_is_better",
        "result_count", "athlete_count", "earliest_result_date", "latest_result_date",
        "mean", "median", "minimum", "maximum", "standard_deviation",
    ]
    return pd.DataFrame(records, columns=columns).sort_values(
        ["result_count", "test_id"], ascending=[False, True], ignore_index=True
    )


def athlete_statistics(data: AuditData) -> pd.DataFrame:
    grouped = {
        athlete_id: group
        for athlete_id, group in data.results.groupby("athlete_id", dropna=False)
    }
    rows: list[dict[str, object]] = []
    for athlete in data.athletes.to_dict("records"):
        group = grouped.get(athlete["id"], data.results.iloc[0:0])
        counts = group["test_id"].value_counts().sort_index()
        rows.append(
            {
                "athlete_id": athlete["id"],
                "birth_year": athlete["birth_year"],
                "gender": athlete["gender"],
                "active": athlete["active"],
                "result_count": len(group),
                "distinct_test_count": int(group["test_id"].nunique()),
                "earliest_result_date": _as_date(group["result_date"].min()),
                "latest_result_date": _as_date(group["result_date"].max()),
                "measurements_per_test": "; ".join(
                    f"{test_id}:{count}" for test_id, count in counts.items()
                ),
            }
        )
    columns = [
        "athlete_id", "birth_year", "gender", "active", "result_count",
        "distinct_test_count", "earliest_result_date", "latest_result_date",
        "measurements_per_test",
    ]
    return pd.DataFrame(rows, columns=columns).sort_values("athlete_id", ignore_index=True)


def coverage_analysis(data: AuditData) -> tuple[pd.DataFrame, pd.DataFrame]:
    test_ids = data.tests["id"].tolist()
    athlete_tests = data.results[["athlete_id", "test_id"]].drop_duplicates()
    matrix = pd.DataFrame(0, index=test_ids, columns=test_ids, dtype=int)
    for first_id in test_ids:
        first_athletes = set(
            athlete_tests.loc[athlete_tests["test_id"] == first_id, "athlete_id"]
        )
        for second_id in test_ids:
            second_athletes = set(
                athlete_tests.loc[athlete_tests["test_id"] == second_id, "athlete_id"]
            )
            matrix.loc[first_id, second_id] = len(first_athletes & second_athletes)
    matrix.index.name = "test_id"
    matrix.columns.name = "test_id"

    test_count_per_athlete = athlete_tests.groupby("athlete_id")["test_id"].nunique()
    distribution = pd.DataFrame(
        [
            {"distinct_tests": "1", "athlete_count": int((test_count_per_athlete == 1).sum())},
            {"distinct_tests": "2", "athlete_count": int((test_count_per_athlete == 2).sum())},
            {"distinct_tests": "3 or more", "athlete_count": int((test_count_per_athlete >= 3).sum())},
        ]
    )
    return matrix, distribution


def _nearest_pair_by_athlete(first: pd.DataFrame, second: pd.DataFrame) -> pd.DataFrame:
    candidates = first.merge(second, on="athlete_id", suffixes=("_first", "_second"))
    if candidates.empty:
        return candidates.assign(gap_days=pd.Series(dtype="int64"))
    candidates["gap_days"] = (
        candidates["result_date_first"] - candidates["result_date_second"]
    ).abs().dt.days
    return (
        candidates.sort_values(
            ["athlete_id", "gap_days", "result_date_first", "result_date_second", "id_first", "id_second"]
        )
        .drop_duplicates("athlete_id", keep="first")
    )


def cross_test_analysis(
    data: AuditData,
    windows_days: tuple[int, ...] = WINDOWS_DAYS,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Summarize nearest cross-test dates and paired observations per cutoff."""
    measurements = data.results.copy()
    measurements["result_date"] = pd.to_datetime(measurements["result_date"])
    tests = data.tests[["id", "name"]].set_index("id")["name"].to_dict()
    gaps: list[dict[str, object]] = []
    windows: list[dict[str, object]] = []
    correlations: list[dict[str, object]] = []

    for first_id, second_id in combinations(data.tests["id"].tolist(), 2):
        first = measurements.loc[
            measurements["test_id"] == first_id,
            ["id", "athlete_id", "result_date", "value"],
        ].rename(columns={"value": "value_first"})
        second = measurements.loc[
            measurements["test_id"] == second_id,
            ["id", "athlete_id", "result_date", "value"],
        ].rename(columns={"value": "value_second"})
        candidates = first.merge(second, on="athlete_id", suffixes=("_first", "_second"))
        candidates["gap_days"] = (
            candidates["result_date_first"] - candidates["result_date_second"]
        ).abs().dt.days
        closest = _nearest_pair_by_athlete(first, second)
        nearest_result_pairs = set(
            zip(closest["athlete_id"], closest["id_first"], closest["id_second"])
        )
        for pair in candidates.to_dict("records"):
            gaps.append(
                {
                    "test_id_1": first_id,
                    "test_name_1": tests[first_id],
                    "test_id_2": second_id,
                    "test_name_2": tests[second_id],
                    "athlete_id": pair["athlete_id"],
                    "result_id_1": pair["id_first"],
                    "result_id_2": pair["id_second"],
                    "date_1": _as_date(pair["result_date_first"]),
                    "date_2": _as_date(pair["result_date_second"]),
                    "gap_days": int(pair["gap_days"]),
                    "nearest_pair_for_athlete": (
                        pair["athlete_id"], pair["id_first"], pair["id_second"]
                    ) in nearest_result_pairs,
                }
            )

        for days in windows_days:
            available = candidates.loc[candidates["gap_days"] <= days]
            selected = closest.loc[closest["gap_days"] <= days]
            windows.append(
                {
                    "test_id_1": first_id,
                    "test_name_1": tests[first_id],
                    "test_id_2": second_id,
                    "test_name_2": tests[second_id],
                    "window_days": days,
                    "result_pair_count": len(available),
                    "distinct_athlete_count": int(available["athlete_id"].nunique()),
                    "one_nearest_pair_per_athlete": len(selected),
                }
            )
            for method in ("pearson", "spearman"):
                coefficient = np.nan
                if len(selected) >= 2:
                    first_values = pd.to_numeric(selected["value_first"], errors="coerce")
                    second_values = pd.to_numeric(selected["value_second"], errors="coerce")
                    if first_values.nunique() > 1 and second_values.nunique() > 1:
                        if method == "spearman":
                            first_values = first_values.rank()
                            second_values = second_values.rank()
                        coefficient = first_values.corr(second_values, method="pearson")
                correlations.append(
                    {
                        "test_id_1": first_id,
                        "test_name_1": tests[first_id],
                        "test_id_2": second_id,
                        "test_name_2": tests[second_id],
                        "paired_observation_count": len(selected),
                        "distinct_athlete_count": int(selected["athlete_id"].nunique()),
                        "time_window_days": days,
                        "correlation_method": method,
                        "correlation": coefficient,
                        "reliability_warning": (
                            "Very small sample; exploratory only"
                            if selected["athlete_id"].nunique() < 10
                            else ""
                        ),
                    }
                )
    return pd.DataFrame(gaps), pd.DataFrame(windows), pd.DataFrame(correlations)


def temporal_statistics(
    data: AuditData,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    results = data.results.copy()
    results["result_date"] = pd.to_datetime(results["result_date"])
    results = results.merge(
        data.athletes[["id", "active"]].rename(columns={"id": "athlete_id"}),
        on="athlete_id",
        how="left",
    )
    results["year"] = results["result_date"].dt.year
    results["month"] = results["result_date"].dt.to_period("M").astype(str)
    results["period"] = results["year"].astype(str)
    results["currently_active_athlete_id"] = results["athlete_id"].where(
        results["active"].fillna(False)
    )
    monthly = results.groupby("month", as_index=False).agg(
        result_count=("id", "count"),
        currently_active_athlete_count=("currently_active_athlete_id", "nunique"),
        athlete_count=("athlete_id", "nunique"),
    ).rename(columns={"month": "period"}).reindex(
        columns=["period", "result_count", "currently_active_athlete_count", "athlete_count"]
    )
    yearly = results.groupby("year", as_index=False).agg(
        result_count=("id", "count"),
        currently_active_athlete_count=("currently_active_athlete_id", "nunique"),
        athlete_count=("athlete_id", "nunique"),
    ).reindex(
        columns=["year", "result_count", "currently_active_athlete_count", "athlete_count"]
    )
    athlete_period = (
        results.groupby(["athlete_id", "period", "month"], as_index=False)
        .agg(result_count=("id", "count"), distinct_test_count=("test_id", "nunique"))
    ).reindex(
        columns=["athlete_id", "period", "month", "result_count", "distinct_test_count"]
    )
    repeats = results.sort_values("result_date").drop_duplicates(
        ["athlete_id", "test_id", "result_date"]
    )
    repeats["interval_days"] = (
        repeats.groupby(["athlete_id", "test_id"])["result_date"].diff().dt.days
    )
    repeat_intervals = (
        repeats.dropna(subset=["interval_days"])
        .groupby("test_id", as_index=False)
        .agg(
            repeated_date_intervals=("interval_days", "count"),
            median_interval_days=("interval_days", "median"),
            minimum_interval_days=("interval_days", "min"),
            maximum_interval_days=("interval_days", "max"),
            athletes_with_repeats=("athlete_id", "nunique"),
        )
    ).reindex(
        columns=[
            "test_id", "repeated_date_intervals", "median_interval_days",
            "minimum_interval_days", "maximum_interval_days", "athletes_with_repeats",
        ]
    )
    sequences_with_three_dates = (
        repeats.groupby(["test_id", "athlete_id"])
        .size()
        .ge(3)
        .groupby(level="test_id")
        .sum()
        .rename("athlete_test_sequences_with_3plus_dates")
    )
    repeat_intervals = (
        data.tests[["id"]]
        .rename(columns={"id": "test_id"})
        .merge(repeat_intervals, on="test_id", how="left")
        .merge(sequences_with_three_dates, on="test_id", how="left")
    )
    repeat_intervals["athlete_test_sequences_with_3plus_dates"] = (
        repeat_intervals["athlete_test_sequences_with_3plus_dates"].fillna(0).astype(int)
    )
    return monthly, yearly, athlete_period, repeat_intervals


def data_quality_issues(data: AuditData) -> pd.DataFrame:
    issues: list[dict[str, object]] = []

    def add(issue_type: str, severity: str, context: dict[str, object], note: str) -> None:
        issues.append(
            {"issue_type": issue_type, "severity": severity, **context, "note": note}
        )

    athlete_ids, test_ids = set(data.athletes["id"]), set(data.tests["id"])
    for result in data.results.to_dict("records"):
        context = {
            "result_id": result["id"],
            "athlete_id": result["athlete_id"],
            "test_id": result["test_id"],
            "result_date": _as_date(result["result_date"]),
            "value": result["value"],
        }
        if result["athlete_id"] not in athlete_ids:
            add("missing_athlete_reference", "high", context, "Result athlete ID is not in the extracted athlete table.")
        if result["test_id"] not in test_ids:
            add("missing_test_reference", "high", context, "Result test ID is not in the extracted test table.")
        if result["athlete_id"] in athlete_ids:
            athlete = data.athletes.loc[data.athletes["id"] == result["athlete_id"]].iloc[0]
            if pd.Timestamp(result["result_date"]).year < int(athlete["birth_year"]):
                add("result_before_birth_year", "review", context, "Result year predates the athlete's recorded birth year.")
        if not pd.isna(result["value"]) and float(result["value"]) <= 0:
            add("non_positive_result", "high", context, "Schema documentation requires positive test-result values.")
        if str(result["type"]) not in KNOWN_RESULT_TYPES:
            add("unexpected_result_type", "review", context, "Result type is outside the documented enum values.")

    for athlete in data.athletes.to_dict("records"):
        year = int(date.today().year)
        if pd.isna(athlete["birth_year"]) or not 1900 <= int(athlete["birth_year"]) <= year:
            add(
                "implausible_birth_year",
                "review",
                {"athlete_id": athlete["id"], "birth_year": athlete["birth_year"]},
                "Potentially implausible birth year; review without changing the source record.",
            )
        if str(athlete["gender"]) not in KNOWN_GENDERS:
            add("unexpected_gender", "review", {"athlete_id": athlete["id"]}, "Gender value is outside the documented enum values.")

    results = data.results.copy()
    duplicate_key = ["athlete_id", "test_id", "result_date", "value", "type"]
    for _, group in results.groupby(duplicate_key, dropna=False):
        if len(group) > 1:
            for result in group.to_dict("records"):
                add(
                    "duplicate_looking_result",
                    "review",
                    {
                        "result_id": result["id"],
                        "athlete_id": result["athlete_id"],
                        "test_id": result["test_id"],
                        "result_date": _as_date(result["result_date"]),
                        "value": result["value"],
                    },
                    "Same athlete, test, date, value and type as another row; may be a legitimate attempt.",
                )

    for test in data.tests.to_dict("records"):
        group = results.loc[results["test_id"] == test["id"]]
        context = {"test_id": test["id"], "test_name": test["name"]}
        if group.empty:
            add("test_without_results", "info", context, "Test definition has no test results.")
        if str(test["unit"]) not in KNOWN_UNITS:
            add("unexpected_unit", "review", context, "Unit is outside the documented canonical units.")
        expected_unit = EXPECTED_UNITS_BY_CATEGORY.get(str(test["category"]))
        if expected_unit is None:
            add("unexpected_category", "review", context, "Category is outside the documented test categories.")
        elif str(test["unit"]) != expected_unit:
            add(
                "category_unit_mismatch",
                "review",
                context,
                f"Documented {test['category']} tests use {expected_unit!r}; review the metadata.",
            )
        if not bool(test["active"]) and not group.empty:
            add("inactive_test_with_results", "info", context, "Inactive test has historical results; this is expected to be preserved.")
        values = pd.to_numeric(group["value"], errors="coerce").dropna()
        if len(values) >= 4:
            q1, q3 = values.quantile([0.25, 0.75])
            spread = q3 - q1
            lower, upper = q1 - 1.5 * spread, q3 + 1.5 * spread
            outliers = group.loc[(values < lower) | (values > upper)]
            for result in outliers.to_dict("records"):
                add(
                    "potential_value_outlier",
                    "review",
                    {
                        "result_id": result["id"],
                        "athlete_id": result["athlete_id"],
                        "test_id": result["test_id"],
                        "result_date": _as_date(result["result_date"]),
                        "value": result["value"],
                    },
                    f"Outside this test's 1.5-IQR range [{lower:g}, {upper:g}]; statistical flag only, not a domain limit.",
                )
    columns = ["issue_type", "severity", "result_id", "athlete_id", "test_id", "test_name", "birth_year", "result_date", "value", "note"]
    return pd.DataFrame(issues).reindex(columns=columns)
