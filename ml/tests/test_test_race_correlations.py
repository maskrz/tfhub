from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.test_race_correlations import correlate_tests
from tfhub_ml.data.extract import AuditData, CompetitionData


def fixture():
    athletes = pd.DataFrame([{"id": 1}, {"id": 2}, {"id": 3}])
    tests = pd.DataFrame([
        {"id": 1, "name": "Jump", "unit": "m", "higher_is_better": True},
        {"id": 2, "name": "Unused", "unit": "s", "higher_is_better": False},
    ])
    results = pd.DataFrame([
        {"id": 1, "athlete_id": 1, "test_id": 1, "result_date": "2025-01-01", "value": 1},
        {"id": 2, "athlete_id": 1, "test_id": 1, "result_date": "2025-01-01", "value": 2},
        {"id": 3, "athlete_id": 2, "test_id": 1, "result_date": "2025-01-01", "value": 3},
        {"id": 4, "athlete_id": 3, "test_id": 1, "result_date": "2025-01-01", "value": 4},
    ])
    events = pd.DataFrame([{"id": 20, "name": "100 m", "unit": "s"}])
    races = pd.DataFrame([
        {"id": 11, "athlete_id": 1, "event_id": 20, "result_date": "2025-01-10", "numeric_value": 14, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 12, "athlete_id": 2, "event_id": 20, "result_date": "2025-01-10", "numeric_value": 13, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 13, "athlete_id": 3, "event_id": 20, "result_date": "2025-01-10", "numeric_value": 12, "unit": "s", "season_type": "UNKNOWN"},
    ])
    return AuditData(athletes, tests, results, 0), CompetitionData(events, races)


def test_correlations_use_best_latest_attempt_and_confirmed_outdoor():
    data, competition = fixture()
    coverage, result = correlate_tests(data, competition, 20, date(2025, 12, 31), frozenset({13}), 30)
    assert coverage.loc[0, "observed_dates"] == 3
    result = result.set_index(["test_id", "method"])
    row = result.loc[(1, "pearson")]
    assert row["paired_dates"] == 3
    assert row["distinct_athletes"] == 3
    assert row["distinct_test_results"] == 3
    assert row["median_test_age_days"] == 1
    assert row["correlation"] == pytest.approx(-1)
    assert result.loc[(1, "spearman"), "correlation"] == pytest.approx(-1)
    assert pd.isna(result.loc[(2, "pearson"), "correlation"])
    assert result.loc[(2, "pearson"), "missing_or_unusable_test_dates"] == 3


def test_window_unconfirmed_and_latest_invalid_are_not_silently_substituted():
    data, competition = fixture()
    extra = pd.DataFrame([
        {"id": 5, "athlete_id": 1, "test_id": 1, "result_date": "2025-01-03", "value": 0},
    ])
    data = AuditData(data.athletes, data.tests, pd.concat([data.results, extra]), 0)
    coverage, result = correlate_tests(data, competition, 20, date(2025, 12, 31), frozenset(), 30)
    assert coverage.loc[0, "needs_review"] == 1
    row = result.set_index(["test_id", "method"]).loc[(1, "pearson")]
    assert row["paired_dates"] == 2
    assert row["unusable_latest_test_dates"] == 1


def test_prediction_date_is_not_used_and_incomplete_windows_excluded():
    data, competition = fixture()
    extra = pd.DataFrame([
        {"id": 6, "athlete_id": 1, "test_id": 1, "result_date": "2025-01-02", "value": 100},
    ])
    data = AuditData(data.athletes, data.tests, pd.concat([data.results, extra]), 0)
    _, result = correlate_tests(data, competition, 20, date(2025, 12, 31), frozenset({13}), 30)
    row = result.set_index(["test_id", "method"]).loc[(1, "pearson")]
    assert row["paired_dates"] == 4
    assert row["distinct_target_races"] == 3
    assert row["distinct_test_results"] == 4
    coverage, _ = correlate_tests(data, competition, 20, date(2025, 1, 31), frozenset({13}), 30)
    assert coverage.loc[0, "observed_dates"] == 0
