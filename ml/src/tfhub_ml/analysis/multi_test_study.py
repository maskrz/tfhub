"""Chronological comparisons of past-race baseline and test-enhanced models."""

from dataclasses import dataclass
from datetime import date

import numpy as np
import pandas as pd

from tfhub_ml.analysis.near_term_100m import _metrics, build_examples
from tfhub_ml.data.extract import AuditData, CompetitionData

TESTS = {
    "spikes_60m": (6, "60m w kolcach", "s", False),
    "shoes_40m": (2, "40m w butach", "s", False),
    "standing_long_jump": (3, "skok w dal z miejsca", "m", True),
    "ball_throw_2kg_backward": (4, "piłka lekarska 2kg w tył", "m", True),
}


@dataclass(frozen=True)
class Comparison:
    coverage: pd.DataFrame
    metrics: pd.DataFrame


def build_feature_examples(data: AuditData, competition: CompetitionData, event_id: int,
                           as_of: date, confirmed_ids: frozenset[int], horizon: int) -> pd.DataFrame:
    for label, (test_id, name, unit, direction) in TESTS.items():
        found = data.tests.loc[data.tests["id"] == test_id]
        if (len(found) != 1 or str(found.iloc[0]["name"]).strip().casefold() != name.casefold()
                or found.iloc[0]["unit"] != unit
                or pd.isna(found.iloc[0]["higher_is_better"])
                or bool(found.iloc[0]["higher_is_better"]) != direction):
            raise ValueError(f"Test metadata for {label} does not match the reviewed definition.")
    examples = build_examples(data, competition, event_id, None, as_of, confirmed_ids, horizon)
    history = data.results.copy()
    history["result_date"] = pd.to_datetime(history["result_date"])
    history["value"] = pd.to_numeric(history["value"], errors="coerce")
    for label, (test_id, _, _, higher) in TESTS.items():
        values = []
        test = history.loc[history["test_id"] == test_id]
        for row in examples.itertuples(index=False):
            prior = test.loc[(test["athlete_id"] == row.athlete_id) & (test["result_date"] < row.prediction_date)]
            value = np.nan
            if not prior.empty:
                latest = prior.loc[prior["result_date"] == prior["result_date"].max()]
                if latest["value"].notna().all() and np.isfinite(latest["value"]).all() and (latest["value"] > 0).all():
                    value = latest["value"].max() if higher else latest["value"].min()
            values.append(value)
        examples[label] = values
    return examples


def compare_examples(examples: pd.DataFrame) -> Comparison:
    observed = examples.loc[(examples["status"] == "observed") & examples["previous_time"].notna()].copy()
    feature_sets = {name: [name] for name in TESTS}
    feature_sets["all_four"] = list(TESTS)
    # Keep index-keyed predictions transient; evaluation rows are always paired with the baseline.
    predictions: dict[str, dict[object, float]] = {}
    coverage = []
    for name, features in feature_sets.items():
        cols = ["previous_time", *features]
        eligible = observed.dropna(subset=cols)
        predictions[name] = {}
        insufficient = rank_deficient = 0
        for index, row in eligible.iterrows():
            train = eligible.loc[(eligible["window_end"] < row["prediction_date"])
                                 & (eligible["athlete_id"] != row["athlete_id"])]
            # There must be more distinct training athletes than fitted coefficients.
            if train["athlete_id"].nunique() <= len(cols) + 1:
                insufficient += 1
                continue
            x = train[cols].to_numpy(dtype=float)
            center = x.mean(axis=0)
            scale = x.std(axis=0)
            if np.any(scale == 0):
                rank_deficient += 1
                continue
            design = np.column_stack((np.ones(len(x)), (x - center) / scale))
            coefficients, _, rank, _ = np.linalg.lstsq(design, train["target"].to_numpy(dtype=float), rcond=None)
            if rank != design.shape[1]:
                rank_deficient += 1
                continue
            prediction = np.dot(np.r_[1.0, (row[cols].to_numpy(dtype=float) - center) / scale], coefficients)
            if not np.isfinite(prediction):
                raise ValueError("A model produced a non-finite prediction.")
            predictions[name][index] = float(prediction)
        coverage.append({
            "model": name, "eligible_dates": len(eligible), "eligible_athletes": eligible["athlete_id"].nunique(),
            "evaluated_dates": len(predictions[name]),
            "evaluated_athletes": observed.loc[list(predictions[name]), "athlete_id"].nunique(),
            "insufficient_training_athletes": insufficient, "constant_or_rank_deficient_training": rank_deficient,
        })
    metrics = []
    for name, forecast in predictions.items():
        rows = observed.loc[list(forecast)]
        if rows.empty:
            continue
        metrics.append(_metrics("latest_100m", name, rows, (rows["previous_time"] - rows["target"]).tolist()))
        metrics.append(_metrics("baseline_plus_" + name, name, rows,
                                [forecast[index] - target for index, target in rows["target"].items()]))
    common = set(observed.index)
    for forecast in predictions.values():
        common.intersection_update(forecast)
    common_index = [index for index in observed.index if index in common]
    rows = observed.loc[common_index]
    if not rows.empty:
        metrics.append(_metrics("latest_100m", "all_models_common", rows, (rows["previous_time"] - rows["target"]).tolist()))
        for name, forecast in predictions.items():
            metrics.append(_metrics("baseline_plus_" + name, "all_models_common", rows,
                                    [forecast[index] - target for index, target in rows["target"].items()]))
    coverage.append({
        "model": "all_models_common", "eligible_dates": len(observed.dropna(subset=list(TESTS))),
        "eligible_athletes": observed.dropna(subset=list(TESTS))["athlete_id"].nunique(),
        "evaluated_dates": len(rows), "evaluated_athletes": rows["athlete_id"].nunique(),
        "insufficient_training_athletes": None, "constant_or_rank_deficient_training": None,
    })
    columns = ["cohort", "method", "examples", "athletes", "mae_seconds", "athlete_weighted_mae_seconds",
               "median_absolute_error_seconds", "max_absolute_error_seconds"]
    return Comparison(pd.DataFrame(coverage), pd.DataFrame(metrics, columns=columns))


def run_comparison(data: AuditData, competition: CompetitionData, event_id: int,
                   as_of: date, confirmed_ids: frozenset[int], horizon: int) -> Comparison:
    return compare_examples(build_feature_examples(data, competition, event_id, as_of, confirmed_ids, horizon))
