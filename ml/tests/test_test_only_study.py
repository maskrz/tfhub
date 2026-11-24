import numpy as np
import pandas as pd
import pytest

from tfhub_ml.analysis.test_only_study import evaluate_test_only


def examples():
    rows = []
    for athlete, feature in [(1, 1.), (2, 2.), (3, 3.), (4, 4.)]:
        rows.append({
            "athlete_id": athlete, "prediction_date": pd.Timestamp("2025-01-01"),
            "window_end": pd.Timestamp("2025-01-31"), "status": "observed",
            "target": 10 + feature, "previous_time": np.nan,
            "spikes_60m": feature, "shoes_40m": np.nan,
            "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
        })
    rows.append({
        "athlete_id": 1, "prediction_date": pd.Timestamp("2025-02-01"),
        "window_end": pd.Timestamp("2025-03-03"), "status": "observed",
        "target": 15., "previous_time": np.nan,
        "spikes_60m": 5., "shoes_40m": np.nan,
        "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
    })
    return pd.DataFrame(rows)


def test_matched_test_and_mean_with_no_prior_race_and_held_out_athlete():
    result = evaluate_test_only(examples())
    coverage = result.coverage.set_index("features")
    assert len(coverage) == 15
    assert coverage.loc["spikes_60m", "eligible_dates"] == 5
    assert coverage.loc["spikes_60m", "evaluated_dates"] == 1
    assert coverage.loc["spikes_60m", "evaluated_athletes_without_prior_race"] == 1
    assert coverage.loc["spikes_60m+shoes_40m", "evaluated_dates"] == 0
    metrics = result.metrics.set_index(["features", "cohort", "method"])
    assert metrics.loc[("spikes_60m", "no_previous_100m", "test_linear"), "mae_seconds"] == pytest.approx(0, abs=1e-12)
    assert metrics.loc[("spikes_60m", "all", "training_mean"), "mae_seconds"] == pytest.approx(2)
    assert metrics.loc[("spikes_60m", "all", "test_linear"), "examples"] == 1


def test_maturity_and_held_out_athlete_prevent_training_leakage():
    data = examples()
    data.loc[1, "window_end"] = pd.Timestamp("2025-02-01")
    result = evaluate_test_only(data)
    assert result.coverage.set_index("features").loc["spikes_60m", "evaluated_dates"] == 0
    assert result.metrics.empty


def test_missing_features_and_singular_folds_are_not_imputed():
    data = examples()
    extra = data.iloc[3].copy()
    extra["athlete_id"] = 5
    data = pd.concat([data, extra.to_frame().T], ignore_index=True)
    data["shoes_40m"] = data["spikes_60m"] * 2
    result = evaluate_test_only(data)
    coverage = result.coverage.set_index("features")
    assert coverage.loc["spikes_60m+shoes_40m", "constant_or_rank_deficient_training"] == 1
    assert coverage.loc["spikes_60m+shoes_40m", "evaluated_dates"] == 0
    data.loc[4, "spikes_60m"] = np.nan
    assert evaluate_test_only(data).coverage.set_index("features").loc["spikes_60m", "eligible_dates"] == 5


def test_empty_examples_return_clear_coverage_and_no_metrics():
    result = evaluate_test_only(examples().iloc[0:0])
    assert len(result.coverage) == 15
    assert result.coverage["evaluated_dates"].sum() == 0
    assert result.metrics.empty
