"""Fit and evaluate explicitly selected, experimental 100 m model subsets."""

from datetime import date
from itertools import combinations
import math

import numpy as np
import pandas as pd

from tfhub_ml.analysis.multi_test_study import TESTS, build_feature_examples
from tfhub_ml.analysis.near_term_100m import _metrics
from tfhub_ml.analysis.within_athlete import build_season_examples
from tfhub_ml.data.extract import AuditData, CompetitionData

TARGETS = ("next_30_days", "season_best")


def build_training_examples(data: AuditData, competition: CompetitionData, event_id: int,
                            as_of: date, confirmed: frozenset[int]) -> dict[str, pd.DataFrame]:
    near = build_feature_examples(data, competition, event_id, as_of, confirmed, 30)
    anchors = build_feature_examples(data, competition, event_id, as_of, confirmed, 1)
    season = build_season_examples(anchors, competition, event_id, as_of, confirmed)
    features = anchors[["athlete_id", "prediction_date", *TESTS]]
    season = season.merge(features, on=["athlete_id", "prediction_date"], validate="one_to_one")
    return {"next_30_days": near, "season_best": season}


def fit_subset(rows: pd.DataFrame, features: list[str]) -> tuple[dict | None, str | None]:
    """Fit standardized OLS only with sufficient distinct athletes and full rank."""
    if rows["athlete_id"].nunique() <= len(features) + 1:
        return None, "insufficient_training_athletes"
    x = rows[features].to_numpy(dtype=float)
    y = rows["target"].to_numpy(dtype=float)
    if not np.isfinite(x).all() or not np.isfinite(y).all() or (x <= 0).any() or (y <= 0).any():
        raise ValueError("Training features and targets must be finite and positive.")
    center, scale = x.mean(axis=0), x.std(axis=0)
    if (scale == 0).any():
        return None, "constant_or_rank_deficient_training"
    design = np.column_stack((np.ones(len(x)), (x - center) / scale))
    coefficients, _, rank, _ = np.linalg.lstsq(design, y, rcond=None)
    if rank != design.shape[1]:
        return None, "constant_or_rank_deficient_training"
    if not np.isfinite(coefficients).all():
        raise ValueError("Fitting produced non-finite coefficients.")
    return {
        "features": features,
        "center": center.tolist(),
        "scale": scale.tolist(),
        "coefficients": coefficients.tolist(),
        "feature_min": x.min(axis=0).tolist(),
        "feature_max": x.max(axis=0).tolist(),
        "training_examples": len(rows),
        "training_athletes": int(rows["athlete_id"].nunique()),
        "training_target_mean": float(y.mean()),
    }, None


def raw_prediction(model: dict, values: list[float]) -> float:
    center = np.asarray(model["center"], dtype=float)
    scale = np.asarray(model["scale"], dtype=float)
    coef = np.asarray(model["coefficients"], dtype=float)
    x = np.asarray(values, dtype=float)
    if (len(center) != len(x) or len(scale) != len(x) or len(coef) != len(x) + 1
            or not np.isfinite(center).all() or not np.isfinite(scale).all()
            or not np.isfinite(coef).all() or (scale <= 0).any()
            or not np.isfinite(x).all() or (x <= 0).any()):
        raise ValueError("Invalid model coefficients or feature values.")
    result = float(np.dot(np.r_[1.0, (x - center) / scale], coef))
    if not math.isfinite(result) or result <= 0:
        raise ValueError("The experimental model produced an invalid, nonpositive prediction.")
    return result


def train_and_evaluate(examples: dict[str, pd.DataFrame], as_of: date, event_id: int) -> dict:
    """Save full-snapshot fits and independent chronological evaluation summaries."""
    bundle = {
        "format_version": 1,
        "status": "experimental_not_for_production",
        "as_of": as_of.isoformat(),
        "event_id": event_id,
        "test_definitions": {
            name: {"id": test_id, "name": label, "unit": unit, "higher_is_better": higher}
            for name, (test_id, label, unit, higher) in TESTS.items()
        },
        "targets": {},
    }
    for target in TARGETS:
        observed = examples[target].loc[examples[target]["status"] == "observed"].copy()
        if not observed.empty and (observed["window_end"] > pd.Timestamp(as_of)).any():
            raise ValueError("Training contains an outcome that has not matured by the cutoff.")
        subsets = {}
        for size in range(1, len(TESTS) + 1):
            for selected in combinations(TESTS, size):
                features = list(selected)
                key = "+".join(features)
                eligible = observed.dropna(subset=features)
                model, reason = fit_subset(eligible, features)
                forecasts = {}
                skipped = {"insufficient_training_athletes": 0, "constant_or_rank_deficient_training": 0,
                           "invalid_prediction": 0}
                for index, row in eligible.iterrows():
                    train = eligible.loc[(eligible["window_end"] < row["prediction_date"])
                                         & (eligible["athlete_id"] != row["athlete_id"])]
                    fold, fold_reason = fit_subset(train, features)
                    if fold is None:
                        skipped[fold_reason] += 1
                        continue
                    try:
                        predicted = raw_prediction(fold, row[features].to_numpy(dtype=float).tolist())
                    except ValueError as exc:
                        if str(exc) != "The experimental model produced an invalid, nonpositive prediction.":
                            raise
                        skipped["invalid_prediction"] += 1
                        continue
                    baseline = fold["training_target_mean"]
                    if target == "season_best" and pd.notna(row["known_season_best"]):
                        predicted = min(predicted, row["known_season_best"])
                        baseline = min(baseline, row["known_season_best"])
                    forecasts[index] = (baseline, predicted)
                evaluated = eligible.loc[list(forecasts)]
                metrics = []
                for method, position in (("training_mean", 0), ("test_linear", 1)):
                    if not evaluated.empty:
                        errors = [forecasts[index][position] - actual
                                  for index, actual in evaluated["target"].items()]
                        metrics.append(_metrics(method, "all", evaluated, errors))
                no_prior = None
                if target == "next_30_days":
                    no_prior = int(evaluated["previous_time"].isna().sum())
                subsets[key] = {
                    "fit": model, "unavailable_reason": reason,
                    "eligible_examples": len(eligible),
                    "eligible_athletes": int(eligible["athlete_id"].nunique()),
                    "evaluated_examples": len(evaluated),
                    "evaluated_athletes": int(evaluated["athlete_id"].nunique()),
                    "evaluated_without_prior_race": no_prior,
                    "skipped_folds": skipped,
                    "chronological_metrics": metrics,
                }
        bundle["targets"][target] = subsets
    return bundle


def predict(bundle: dict, target: str, feature_values: dict, prediction_date: date,
            known_season_best: tuple[date, float] | None = None) -> dict:
    if bundle.get("format_version") != 1 or bundle.get("status") != "experimental_not_for_production":
        raise ValueError("Unsupported or non-experimental model bundle.")
    if target not in TARGETS:
        raise ValueError("Unsupported target.")
    if prediction_date <= date.fromisoformat(bundle["as_of"]):
        raise ValueError("Prediction date must be after the model training cutoff; this is not a backtesting tool.")
    if target == "season_best" and prediction_date >= date(prediction_date.year, 10, 31):
        raise ValueError("Season-best prediction date must be before October 31.")
    if known_season_best is not None and target != "season_best":
        raise ValueError("Known season best is only applicable to the season-best target.")
    if not feature_values or any(name not in TESTS for name in feature_values):
        raise ValueError("Provide at least one supported feature and no unsupported features.")
    selected = [name for name in TESTS if name in feature_values]
    key = "+".join(selected)
    subset = bundle["targets"][target][key]
    model = subset["fit"]
    if model is None:
        raise ValueError(f"No fitted model for {target}/{key}: {subset['unavailable_reason']}.")
    values, outside = [], []
    for index, name in enumerate(selected):
        entry = feature_values[name]
        measured = date.fromisoformat(entry["date"])
        value = float(entry["value"])
        if measured >= prediction_date:
            raise ValueError("Each test date must be strictly before the prediction date.")
        if not math.isfinite(value) or value <= 0:
            raise ValueError("Test values must be finite and positive.")
        values.append(value)
        if value < model["feature_min"][index] or value > model["feature_max"][index]:
            outside.append(name)
    estimate = raw_prediction(model, values)
    raw = estimate
    if known_season_best is not None:
        race_date, best = known_season_best
        if not (date(prediction_date.year, 5, 1) <= race_date <= date(prediction_date.year, 10, 31)
                and race_date < prediction_date and math.isfinite(best) and best > 0):
            raise ValueError("Known best must be a positive, strictly prior result in the prediction year's outdoor season.")
        estimate = min(estimate, best)
    return {
        "status": "experimental_not_for_production", "target": target,
        "prediction_date": prediction_date.isoformat(), "model_training_cutoff": bundle["as_of"],
        "features": selected, "prediction_seconds": estimate, "raw_model_seconds": raw,
        "features_outside_training_range": outside,
        "training_examples": model["training_examples"], "training_athletes": model["training_athletes"],
        "evaluated_examples": subset["evaluated_examples"], "evaluated_athletes": subset["evaluated_athletes"],
        "warning": "Not validated for individual use; no prediction interval. Review data ages and model limitations.",
    }
