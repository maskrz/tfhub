import pandas as pd
import pytest

from tfhub_ml.analysis.audit import (
    athlete_statistics,
    coverage_analysis,
    cross_test_analysis,
    data_quality_issues,
    temporal_statistics,
    test_statistics as calculate_test_statistics,
)
from tfhub_ml.data.extract import AuditData


def sample_data() -> AuditData:
    athletes = pd.DataFrame(
        [
            {"id": 1, "birth_year": 2000, "gender": "MALE", "active": True, "training_group_id": 1},
            {"id": 2, "birth_year": 2001, "gender": "FEMALE", "active": False, "training_group_id": None},
            {"id": 3, "birth_year": 1999, "gender": "MALE", "active": True, "training_group_id": 1},
        ]
    )
    tests = pd.DataFrame(
        [
            {"id": 10, "name": "Sprint", "category": "TIME", "unit": "s", "higher_is_better": False, "active": True},
            {"id": 20, "name": "Jump", "category": "DISTANCE", "unit": "m", "higher_is_better": True, "active": True},
            {"id": 30, "name": "Unused", "category": "SPEED", "unit": "m/s", "higher_is_better": True, "active": False},
        ]
    )
    results = pd.DataFrame(
        [
            {"id": 100, "athlete_id": 1, "test_id": 10, "result_date": pd.Timestamp("2025-01-01"), "value": 8.0, "type": "TRAINING"},
            {"id": 101, "athlete_id": 1, "test_id": 10, "result_date": pd.Timestamp("2025-02-01"), "value": 7.8, "type": "TRAINING"},
            {"id": 102, "athlete_id": 1, "test_id": 20, "result_date": pd.Timestamp("2025-01-05"), "value": 2.5, "type": "TRAINING"},
            {"id": 103, "athlete_id": 2, "test_id": 10, "result_date": pd.Timestamp("2025-03-01"), "value": 9.0, "type": "TRAINING"},
            {"id": 104, "athlete_id": 3, "test_id": 20, "result_date": pd.Timestamp("2025-04-01"), "value": 2.7, "type": "TRAINING"},
        ]
    )
    return AuditData(athletes, tests, results, 2)


def test_test_and_athlete_statistics_keep_tests_and_athletes_separate() -> None:
    data = sample_data()
    tests = calculate_test_statistics(data).set_index("test_id")
    athletes = athlete_statistics(data).set_index("athlete_id")

    assert tests.loc[10, "result_count"] == 3
    assert tests.loc[10, "athlete_count"] == 2
    assert tests.loc[30, "result_count"] == 0
    assert athletes.loc[1, "result_count"] == 3
    assert athletes.loc[1, "distinct_test_count"] == 2
    assert "name" not in athletes.columns


def test_coverage_matrix_and_distribution_count_distinct_athletes() -> None:
    matrix, distribution = coverage_analysis(sample_data())

    assert matrix.loc[10, 20] == 1
    assert matrix.loc[10, 10] == 2
    counts = dict(zip(distribution["distinct_tests"], distribution["athlete_count"]))
    assert counts == {"1": 2, "2": 1, "3 or more": 0}


def test_cross_test_cutoffs_and_correlations_report_pairing_unit() -> None:
    gaps, windows, correlations = cross_test_analysis(sample_data())

    assert gaps.loc[gaps["athlete_id"] == 1, "gap_days"].iloc[0] == 4
    seven_day = windows.loc[windows["window_days"] == 7].iloc[0]
    assert seven_day["result_pair_count"] == 1
    assert seven_day["distinct_athlete_count"] == 1
    no_overlap = windows.loc[
        (windows["test_id_1"] == 10)
        & (windows["test_id_2"] == 30)
        & (windows["window_days"] == 7)
    ].iloc[0]
    assert no_overlap["result_pair_count"] == 0
    result = correlations.loc[
        (correlations["time_window_days"] == 7)
        & (correlations["correlation_method"] == "pearson")
    ].iloc[0]
    assert result["paired_observation_count"] == 1
    assert result["distinct_athlete_count"] == 1
    assert result["reliability_warning"]


def test_spearman_uses_average_ranks_for_tied_values() -> None:
    data = sample_data()
    values = [(1.0, 4.0), (2.0, 1.0), (2.0, 1.0), (4.0, 3.0)]
    results = pd.DataFrame(
        [
            {
                "id": athlete_id * 10 + test_id,
                "athlete_id": athlete_id,
                "test_id": test_id,
                "result_date": pd.Timestamp("2025-01-01"),
                "value": value,
                "type": "TRAINING",
            }
            for athlete_id, (first, second) in enumerate(values, start=1)
            for test_id, value in ((10, first), (20, second))
        ]
    )
    _, _, correlations = cross_test_analysis(
        AuditData(data.athletes, data.tests, results, data.training_group_count),
        windows_days=(7,),
    )
    row = correlations.loc[
        (correlations["test_id_1"] == 10)
        & (correlations["test_id_2"] == 20)
        & (correlations["correlation_method"] == "spearman")
    ].iloc[0]

    assert row["paired_observation_count"] == 4
    assert row["distinct_athlete_count"] == 4
    assert row["correlation"] == pytest.approx(-1 / 3)


def test_temporal_statistics_return_month_year_athlete_and_repeat_tables() -> None:
    monthly, yearly, athlete_period, repeat_intervals = temporal_statistics(sample_data())

    assert monthly["result_count"].sum() == 5
    assert yearly["result_count"].sum() == 5
    assert not athlete_period.empty
    assert repeat_intervals.loc[0, "repeated_date_intervals"] == 1


def test_duplicate_looking_results_are_review_flags_not_invalid_errors() -> None:
    data = sample_data()
    duplicate = data.results.iloc[[0]].copy()
    duplicate["id"] = 105
    data = AuditData(
        data.athletes,
        data.tests,
        pd.concat([data.results, duplicate], ignore_index=True),
        data.training_group_count,
    )

    issues = data_quality_issues(data)
    duplicate_flags = issues.loc[issues["issue_type"] == "duplicate_looking_result"]

    assert len(duplicate_flags) == 2
    assert set(duplicate_flags["severity"]) == {"review"}
    assert "legitimate attempt" in duplicate_flags.iloc[0]["note"]


def test_empty_result_history_still_reports_zero_pairs_for_every_test_pair() -> None:
    data = sample_data()
    empty = AuditData(
        data.athletes,
        data.tests,
        data.results.iloc[0:0],
        data.training_group_count,
    )

    monthly, yearly, athlete_period, repeat_intervals = temporal_statistics(empty)
    _, windows, correlations = cross_test_analysis(empty)

    assert monthly.empty and yearly.empty and athlete_period.empty
    assert len(repeat_intervals) == len(data.tests)
    assert len(windows) == 3 * 4
    assert (windows["result_pair_count"] == 0).all()
    assert (correlations["paired_observation_count"] == 0).all()
