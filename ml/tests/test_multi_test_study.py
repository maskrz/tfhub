from datetime import date

import numpy as np
import pandas as pd
import pytest

from tfhub_ml.analysis.multi_test_study import TESTS, build_feature_examples, compare_examples
from tfhub_ml.data.extract import AuditData, CompetitionData


def test_feature_selection_uses_latest_best_and_excludes_prediction_date():
    tests = pd.DataFrame([
        {"id": identifier, "name": name, "unit": unit, "higher_is_better": direction}
        for identifier, name, unit, direction in TESTS.values()
    ])
    results = pd.DataFrame([
        {"id": 1, "athlete_id": 1, "test_id": 3, "result_date": "2025-01-01", "value": 2},
        {"id": 2, "athlete_id": 1, "test_id": 3, "result_date": "2025-01-01", "value": 3},
        {"id": 3, "athlete_id": 1, "test_id": 3, "result_date": "2025-01-02", "value": 4},
        {"id": 4, "athlete_id": 1, "test_id": 6, "result_date": "2025-01-01", "value": 8},
        {"id": 5, "athlete_id": 1, "test_id": 6, "result_date": "2025-01-01", "value": 7},
        {"id": 6, "athlete_id": 1, "test_id": 2, "result_date": "2025-01-01", "value": 0},
    ])
    data = AuditData(pd.DataFrame([{"id": 1}]), tests, results, 0)
    competition = CompetitionData(
        pd.DataFrame([{"id": 1, "name": "100 m", "unit": "s"}]),
        pd.DataFrame([{"id": 10, "athlete_id": 1, "event_id": 1, "result_date": "2025-01-10",
                       "numeric_value": 13, "unit": "s", "season_type": "OUTDOOR"}]),
    )
    examples = build_feature_examples(data, competition, 1, date(2025, 12, 31), frozenset(), 30)
    row = examples.loc[examples["prediction_date"] == "2025-01-02"].iloc[0]
    assert row["standing_long_jump"] == 3
    assert row["spikes_60m"] == 7
    assert pd.isna(row["shoes_40m"])
    data.tests.loc[data.tests["id"] == 6, "unit"] = "m"
    with pytest.raises(ValueError, match="metadata"):
        build_feature_examples(data, competition, 1, date(2025, 12, 31), frozenset(), 30)


def test_chronological_matched_evaluation_and_skipped_folds():
    rows = []
    for i, (prior, feature) in enumerate([(10, 1), (12, 1), (10, 3), (12, 3), (11, 2)]):
        rows.append({
            "athlete_id": i + 1, "status": "observed", "prediction_date": pd.Timestamp("2025-01-01"),
            "window_end": pd.Timestamp("2025-01-31"), "previous_time": prior, "target": prior + feature,
            "spikes_60m": feature, "shoes_40m": np.nan,
            "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
        })
    rows.append({
        "athlete_id": 9, "status": "observed", "prediction_date": pd.Timestamp("2025-02-01"),
        "window_end": pd.Timestamp("2025-03-03"), "previous_time": 11, "target": 13,
        "spikes_60m": 2, "shoes_40m": np.nan,
        "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
    })
    examples = pd.DataFrame(rows)
    result = compare_examples(examples)
    coverage = result.coverage.set_index("model")
    assert coverage.loc["spikes_60m", "evaluated_dates"] == 1
    assert coverage.loc["spikes_60m", "insufficient_training_athletes"] == 5
    assert coverage.loc["all_four", "evaluated_dates"] == 0
    metrics = result.metrics.set_index(["cohort", "method"])
    assert metrics.loc[("spikes_60m", "latest_100m"), "mae_seconds"] == pytest.approx(2)
    assert metrics.loc[("spikes_60m", "baseline_plus_spikes_60m"), "mae_seconds"] == pytest.approx(0, abs=1e-12)
    assert metrics.loc[("spikes_60m", "latest_100m"), "athletes"] == 1
    examples.loc[4, "window_end"] = pd.Timestamp("2025-02-01")
    examples.loc[3, "athlete_id"] = 9
    assert compare_examples(examples).coverage.set_index("model").loc["spikes_60m", "evaluated_dates"] == 0


def test_rank_deficient_training_is_not_fitted():
    rows = pd.DataFrame([{
        "athlete_id": i, "status": "observed", "prediction_date": pd.Timestamp("2025-01-01") if i < 6 else pd.Timestamp("2025-02-01"),
        "window_end": pd.Timestamp("2025-01-31"), "previous_time": i, "target": i + 2,
        "spikes_60m": i * 2, "shoes_40m": np.nan, "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
    } for i in range(1, 7)])
    result = compare_examples(rows)
    assert result.coverage.set_index("model").loc["spikes_60m", "constant_or_rank_deficient_training"] == 1
    assert result.metrics.empty


def test_combined_model_and_common_comparison_with_sufficient_distinct_athletes():
    rng = np.random.default_rng(42)
    rows = []
    for i in range(10):
        x = rng.uniform(2, 10, size=5)
        rows.append({
            "athlete_id": i + 1, "status": "observed",
            "prediction_date": pd.Timestamp("2025-01-01") if i < 9 else pd.Timestamp("2025-02-01"),
            "window_end": pd.Timestamp("2025-01-31") if i < 9 else pd.Timestamp("2025-03-03"),
            "previous_time": x[0], "spikes_60m": x[1], "shoes_40m": x[2],
            "standing_long_jump": x[3], "ball_throw_2kg_backward": x[4],
            "target": x[0] + x[1] + x[2] + x[3] + x[4],
        })
    result = compare_examples(pd.DataFrame(rows))
    assert result.coverage.set_index("model").loc["all_models_common", "evaluated_dates"] == 1
    metric = result.metrics.set_index(["cohort", "method"])
    assert metric.loc[("all_models_common", "baseline_plus_all_four"), "mae_seconds"] == pytest.approx(0, abs=1e-10)
    assert len(result.metrics.loc[result.metrics["cohort"] == "all_models_common"]) == 6
