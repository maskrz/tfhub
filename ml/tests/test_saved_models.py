from datetime import date
import json
from pathlib import Path
import subprocess
import sys

import numpy as np
import pandas as pd
import pytest

from tfhub_ml.analysis.saved_models import fit_subset, predict, train_and_evaluate


def examples():
    rows = []
    for athlete, value in [(1, 1.), (2, 2.), (3, 3.), (4, 4.)]:
        rows.append({
            "athlete_id": athlete, "prediction_date": pd.Timestamp("2024-04-02"),
            "window_end": pd.Timestamp("2024-10-31"), "status": "observed",
            "target": 10 + value, "previous_time": np.nan, "known_season_best": np.nan,
            "spikes_60m": value, "shoes_40m": np.nan,
            "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
        })
    rows.append({
        "athlete_id": 1, "prediction_date": pd.Timestamp("2025-04-02"),
        "window_end": pd.Timestamp("2025-10-31"), "status": "observed",
        "target": 15., "previous_time": np.nan, "known_season_best": 14.5,
        "spikes_60m": 5., "shoes_40m": np.nan,
        "standing_long_jump": np.nan, "ball_throw_2kg_backward": np.nan,
    })
    return pd.DataFrame(rows)


def bundle():
    data = examples()
    return train_and_evaluate({"next_30_days": data, "season_best": data}, date(2025, 11, 1), 1)


def test_fits_json_roundtrip_and_chronological_matched_evaluation():
    result = json.loads(json.dumps(bundle(), allow_nan=False))
    near = result["targets"]["next_30_days"]["spikes_60m"]
    season = result["targets"]["season_best"]["spikes_60m"]
    assert near["fit"]["training_athletes"] == 4
    assert near["evaluated_examples"] == 1
    assert near["evaluated_without_prior_race"] == 1
    assert near["chronological_metrics"][1]["mae_seconds"] == pytest.approx(0, abs=1e-12)
    assert season["chronological_metrics"][1]["mae_seconds"] == pytest.approx(.5)
    assert result["targets"]["next_30_days"]["shoes_40m"]["fit"] is None


def test_maturity_and_athlete_exclusion():
    data = examples()
    data.loc[1, "window_end"] = pd.Timestamp("2025-04-02")
    result = train_and_evaluate({"next_30_days": data, "season_best": data}, date(2025, 11, 1), 1)
    assert result["targets"]["next_30_days"]["spikes_60m"]["evaluated_examples"] == 0
    data.loc[0, "window_end"] = pd.Timestamp("2026-01-01")
    with pytest.raises(ValueError, match="not matured"):
        train_and_evaluate({"next_30_days": data, "season_best": data}, date(2025, 11, 1), 1)


def test_missing_and_singular_models_not_fitted():
    data = examples()
    data["shoes_40m"] = 2 * data["spikes_60m"]
    model, reason = fit_subset(data, ["spikes_60m", "shoes_40m"])
    assert model is None and reason == "constant_or_rank_deficient_training"
    model, reason = fit_subset(data.dropna(subset=["standing_long_jump"]), ["standing_long_jump"])
    assert model is None and reason == "insufficient_training_athletes"


def test_prediction_dates_missing_features_range_and_season_best():
    model = bundle()
    features = {"spikes_60m": {"date": "2025-11-02", "value": 6}}
    result = predict(model, "next_30_days", features, date(2025, 11, 3))
    assert result["prediction_seconds"] == pytest.approx(16)
    assert result["features_outside_training_range"] == ["spikes_60m"]
    season = predict(model, "season_best", features, date(2026, 9, 1), (date(2026, 6, 1), 14.))
    assert season["prediction_seconds"] == 14.
    with pytest.raises(ValueError, match="training cutoff"):
        predict(model, "next_30_days", features, date(2025, 11, 1))
    with pytest.raises(ValueError, match="strictly before"):
        predict(model, "next_30_days", features, date(2025, 11, 2))
    with pytest.raises(ValueError, match="No fitted model"):
        predict(model, "next_30_days", {"shoes_40m": features["spikes_60m"]}, date(2025, 11, 3))
    with pytest.raises(ValueError, match="Known best"):
        predict(model, "season_best", features, date(2026, 9, 1), (date(2026, 9, 1), 14.))
    with pytest.raises(ValueError, match="before October 31"):
        predict(model, "season_best", features, date(2026, 11, 3))
    with pytest.raises(ValueError, match="finite and positive"):
        predict(model, "next_30_days", {"spikes_60m": {"date": "2025-11-02", "value": -1}}, date(2025, 11, 3))


def test_prediction_cli_with_saved_json_and_explicit_failure(tmp_path):
    model_path = tmp_path / "model.json"
    input_path = tmp_path / "input.json"
    model_path.write_text(json.dumps(bundle(), allow_nan=False), encoding="utf-8")
    request = {
        "target": "season_best", "prediction_date": "2026-09-01",
        "features": {"spikes_60m": {"date": "2026-08-01", "value": 4.0}},
        "known_season_best": {"date": "2026-06-01", "value": 13.5},
    }
    input_path.write_text(json.dumps(request), encoding="utf-8")
    script = Path(__file__).resolve().parents[1] / "scripts" / "predict_100m.py"
    command = [sys.executable, str(script), "--model", str(model_path), "--input", str(input_path)]
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stderr
    assert json.loads(result.stdout)["prediction_seconds"] == pytest.approx(13.5)
    request["features"] = {"shoes_40m": {"date": "2026-08-01", "value": 4.0}}
    input_path.write_text(json.dumps(request), encoding="utf-8")
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    assert result.returncode != 0
    assert "No fitted model" in result.stderr
