"""Descriptive comparison of recorded and approximate calm-air 100 m times."""

from dataclasses import dataclass

import numpy as np
import pandas as pd


@dataclass(frozen=True)
class WindReview:
    coverage: pd.DataFrame
    metrics: pd.DataFrame
    wind_change: pd.DataFrame


def calm_time(time: pd.Series, wind: pd.Series) -> pd.Series:
    """Apply the coach-supplied rule of thumb, without rounding."""
    return time + wind * (0.05 * time / 10)


def assess_wind(review: pd.DataFrame, races: pd.DataFrame, confirmed_ids: frozenset[int] = frozenset()) -> WindReview:
    if races["id"].duplicated().any():
        raise ValueError("Duplicate competition result IDs prevent wind review.")
    cases = review.copy()
    for prefix, id_column, date_column, time_column in (
        ("previous", "previous_result_id", "previous_result_date", "previous_time"),
        ("target", "target_result_id", "target_result_date", "target"),
    ):
        source = races.rename(columns={
            "id": id_column, "result_date": "source_date", "numeric_value": "source_time",
            "unit": "source_unit", "season_type": "source_season", "wind": prefix + "_wind",
        })
        cases = cases.merge(source, on=id_column, how="left", validate="many_to_one", indicator=True)
        mismatch = (
            cases["_merge"].ne("both")
            | (pd.to_datetime(cases[date_column]) != pd.to_datetime(cases["source_date"]))
            | (pd.to_numeric(cases[time_column]) != pd.to_numeric(cases["source_time"]))
            | cases["source_unit"].ne("s")
            | ~(cases["source_season"].eq("OUTDOOR") |
                (cases["source_season"].eq("UNKNOWN") & cases[id_column].isin(confirmed_ids)))
        )
        if mismatch.any():
            raise ValueError(f"{int(mismatch.sum())} {prefix} results changed or are unavailable; review the snapshot before proceeding.")
        cases = cases.drop(columns=["source_date", "source_time", "source_unit", "source_season", "_merge"])
        cases[prefix + "_wind"] = pd.to_numeric(cases[prefix + "_wind"], errors="raise")

    both = (
        cases["previous_wind"].notna() & cases["target_wind"].notna()
        & np.isfinite(cases["previous_wind"]) & np.isfinite(cases["target_wind"])
    )
    coverage = pd.DataFrame([{
        "baseline_cases": len(cases), "baseline_athletes": cases["athlete_id"].nunique(),
        "cases_with_both_winds": int(both.sum()), "athletes_with_both_winds": cases.loc[both, "athlete_id"].nunique(),
        "excluded_missing_or_nonfinite_wind": int((~both).sum()),
        "unique_previous_races": cases["previous_result_id"].nunique(),
        "unique_target_races": cases["target_result_id"].nunique(),
    }])
    eligible = cases.loc[both].copy()
    eligible["raw_error"] = eligible["previous_time"] - eligible["target"]
    eligible["calm_error"] = (
        calm_time(eligible["previous_time"], eligible["previous_wind"])
        - calm_time(eligible["target"], eligible["target_wind"])
    )
    eligible["wind_change"] = eligible["target_wind"] - eligible["previous_wind"]
    metrics = []
    for label, column in (("recorded_times", "raw_error"), ("approximate_calm_times", "calm_error")):
        errors = eligible[column]
        if errors.empty:
            continue
        by_athlete = eligible.assign(absolute=errors.abs()).groupby("athlete_id")["absolute"].mean()
        metrics.append({
            "comparison": label, "cases": len(eligible), "athletes": eligible["athlete_id"].nunique(),
            "mean_absolute_error_s": errors.abs().mean(),
            "athlete_weighted_mae_s": by_athlete.mean(),
            "median_absolute_error_s": errors.abs().median(),
            "max_absolute_error_s": errors.abs().max(),
            "mean_signed_error_s": errors.mean(),
        })
    changes = []
    for label, group in (
        ("more_headwind", eligible.loc[eligible["wind_change"] < 0]),
        ("unchanged", eligible.loc[eligible["wind_change"] == 0]),
        ("more_tailwind", eligible.loc[eligible["wind_change"] > 0]),
    ):
        changes.append({
            "target_wind_vs_previous": label, "cases": len(group), "athletes": group["athlete_id"].nunique(),
            "mean_wind_change_mps": group["wind_change"].mean(),
            "recorded_mean_signed_error_s": group["raw_error"].mean(),
            "calm_mean_signed_error_s": group["calm_error"].mean(),
            "recorded_mae_s": group["raw_error"].abs().mean(),
            "calm_mae_s": group["calm_error"].abs().mean(),
        })
    metric_columns = ["comparison", "cases", "athletes", "mean_absolute_error_s", "athlete_weighted_mae_s",
                      "median_absolute_error_s", "max_absolute_error_s", "mean_signed_error_s"]
    return WindReview(coverage, pd.DataFrame(metrics, columns=metric_columns), pd.DataFrame(changes))
