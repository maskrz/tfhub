import pandas as pd
import pytest

from tfhub_ml.analysis.wind_review import assess_wind, calm_time


def fixtures():
    cases = pd.DataFrame([
        {"athlete_id": 1, "previous_result_id": 1, "previous_result_date": "2025-01-01",
         "previous_time": 10.5, "target_result_id": 2, "target_result_date": "2025-01-10", "target": 10.4},
        {"athlete_id": 1, "previous_result_id": 1, "previous_result_date": "2025-01-01",
         "previous_time": 10.5, "target_result_id": 3, "target_result_date": "2025-01-11", "target": 10.0},
        {"athlete_id": 2, "previous_result_id": 4, "previous_result_date": "2025-01-01",
         "previous_time": 12.0, "target_result_id": 5, "target_result_date": "2025-01-10", "target": 11.0},
    ])
    races = pd.DataFrame([
        {"id": 1, "result_date": "2025-01-01", "numeric_value": 10.5, "unit": "s", "season_type": "OUTDOOR", "wind": 2.0},
        {"id": 2, "result_date": "2025-01-10", "numeric_value": 10.4, "unit": "s", "season_type": "OUTDOOR", "wind": -1.0},
        {"id": 3, "result_date": "2025-01-11", "numeric_value": 10.0, "unit": "s", "season_type": "OUTDOOR", "wind": None},
        {"id": 4, "result_date": "2025-01-01", "numeric_value": 12.0, "unit": "s", "season_type": "OUTDOOR", "wind": 0.0},
        {"id": 5, "result_date": "2025-01-10", "numeric_value": 11.0, "unit": "s", "season_type": "UNKNOWN", "wind": 1.0},
    ])
    return cases, races


def test_adjustment_sign_and_example():
    assert calm_time(pd.Series([10.5, 10.5, 10.5]), pd.Series([2, -2, 0])).tolist() == pytest.approx([10.605, 10.395, 10.5])


def test_paired_coverage_and_metrics():
    cases, races = fixtures()
    result = assess_wind(cases, races, frozenset({5}))
    assert result.coverage.loc[0, "cases_with_both_winds"] == 2
    assert result.coverage.loc[0, "excluded_missing_or_nonfinite_wind"] == 1
    metrics = result.metrics.set_index("comparison")
    assert metrics.loc["recorded_times", "mean_absolute_error_s"] == pytest.approx(0.55)
    assert metrics.loc["approximate_calm_times", "mean_absolute_error_s"] == pytest.approx((0.257 + 0.945) / 2)
    assert metrics.loc["recorded_times", "athlete_weighted_mae_s"] == pytest.approx(0.55)
    assert result.wind_change.set_index("target_wind_vs_previous").loc["more_headwind", "cases"] == 1


def test_stale_data_and_unconfirmed_unknown_are_rejected():
    cases, races = fixtures()
    with pytest.raises(ValueError, match="changed or are unavailable"):
        assess_wind(cases, races)
    races.loc[races["id"] == 2, "numeric_value"] = 99
    with pytest.raises(ValueError, match="changed or are unavailable"):
        assess_wind(cases, races, frozenset({5}))
