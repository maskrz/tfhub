"""Descriptive timing coverage for a prospective 100 m study."""

from dataclasses import dataclass
from datetime import date

import numpy as np
import pandas as pd

from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class SprintReadiness:
    classifications: pd.DataFrame
    years: pd.DataFrame
    coverage: pd.DataFrame
    windows: pd.DataFrame


def assess_sprint_readiness(
    data: AuditData,
    competition: CompetitionData,
    event_id: int,
    as_of: date,
    windows_days: tuple[int, ...],
    confirmed_ids: frozenset[int] = frozenset(),
) -> SprintReadiness:
    if as_of > date.today():
        raise ValueError("The audit date cannot be in the future.")
    if not windows_days or any(day <= 0 for day in windows_days) or len(set(windows_days)) != len(windows_days):
        raise ValueError("Windows must be distinct positive day counts.")
    event = competition.events.loc[competition.events["id"] == event_id]
    if len(event) != 1 or str(event.iloc[0]["name"]).strip().casefold() != "100 m" or event.iloc[0]["unit"] != "s":
        raise ValueError("The selected event must be the verified 100 m event in seconds.")

    races = competition.results.loc[competition.results["event_id"] == event_id].copy()
    races["result_date"] = pd.to_datetime(races["result_date"])
    races = races.loc[races["result_date"] <= pd.Timestamp(as_of)].copy()
    confirmed = races.loc[races["id"].isin(confirmed_ids)]
    if set(confirmed["id"]) != confirmed_ids or not confirmed["season_type"].eq("UNKNOWN").all():
        raise ValueError("Confirmed IDs must identify UNKNOWN results for the selected 100 m event on or before the cutoff.")
    races["numeric_value"] = pd.to_numeric(races["numeric_value"], errors="coerce")
    races["comparable"] = (
        races["numeric_value"].notna()
        & np.isfinite(races["numeric_value"])
        & (races["numeric_value"] > 0)
        & races["unit"].eq("s")
    )
    races["year"] = races["result_date"].dt.year
    classifications = races.groupby("season_type", dropna=False).agg(
        result_rows=("id", "size"), athletes=("athlete_id", "nunique"),
        comparable_rows=("comparable", "sum"),
    ).reset_index()
    years = races.groupby(["year", "season_type"], dropna=False).agg(
        result_rows=("id", "size"), athletes=("athlete_id", "nunique"),
        comparable_rows=("comparable", "sum"),
    ).reset_index()
    targets = races.loc[races["comparable"] & races["season_type"].isin(["OUTDOOR", "UNKNOWN"])].copy()

    tests = data.results.copy()
    tests["result_date"] = pd.to_datetime(tests["result_date"])
    tests["value"] = pd.to_numeric(tests["value"], errors="coerce")
    tests = tests.loc[
        (tests["result_date"] <= pd.Timestamp(as_of))
        & tests["value"].notna() & np.isfinite(tests["value"]) & (tests["value"] > 0)
    ]
    coverage_rows = []
    window_rows = []
    classifications_to_analyze = ["OUTDOOR", "UNKNOWN"]
    if confirmed_ids:
        classifications_to_analyze.append("OUTDOOR_WITH_CONFIRMED")
    for classification in classifications_to_analyze:
        if classification == "OUTDOOR_WITH_CONFIRMED":
            group = targets.loc[targets["season_type"].eq("OUTDOOR") | targets["id"].isin(confirmed_ids)]
            eligible_sources = races["season_type"].eq("OUTDOOR") | races["id"].isin(confirmed_ids)
        else:
            group = targets.loc[targets["season_type"] == classification]
            eligible_sources = races["season_type"].eq("OUTDOOR")
        sources: list[tuple[str, object, pd.DataFrame]] = [
            ("prior_outdoor_100m", "", races.loc[races["comparable"] & eligible_sources]),
        ]
        for metadata in data.tests.sort_values("id").to_dict("records"):
            sources.append(("test", metadata["id"], tests.loc[tests["test_id"] == metadata["id"]]))
        for source, test_id, observations in sources:
            # Multiple attempts on one date constitute one possible date, not multiple anchors.
            anchors = observations.groupby(["athlete_id", "result_date"]).size().reset_index(name="source_rows").rename(
                columns={"result_date": "anchor_date"}
            )
            pairs = group[["id", "athlete_id", "result_date"]].merge(anchors, on="athlete_id")
            pairs["gap_days"] = (pairs["result_date"] - pairs["anchor_date"]).dt.days
            if source == "prior_outdoor_100m" and classification in ("OUTDOOR", "OUTDOOR_WITH_CONFIRMED"):
                pairs = pairs.loc[(pairs["gap_days"] != 0) | (pairs["source_rows"] > 1)]
            prior = pairs.loc[pairs["gap_days"] > 0]
            closest = prior.groupby("id")["gap_days"].min()
            coverage_rows.append({
                "target_classification": classification, "source": source, "test_id": test_id,
                "target_rows": len(group), "target_athletes": group["athlete_id"].nunique(),
                "targets_with_prior": prior["id"].nunique(),
                "athletes_with_prior": prior["athlete_id"].nunique(),
                "targets_with_same_day_source": pairs.loc[pairs["gap_days"] == 0, "id"].nunique(),
                "nearest_prior_min_days": closest.min() if not closest.empty else None,
                "nearest_prior_median_days": closest.median() if not closest.empty else None,
                "nearest_prior_max_days": closest.max() if not closest.empty else None,
            })
            for days in windows_days:
                within = prior.loc[prior["gap_days"] <= days]
                window_rows.append({
                    "target_classification": classification, "source": source, "test_id": test_id,
                    "window_days": days, "anchor_target_pairs": len(within),
                    "distinct_athlete_anchor_dates": len(within[["athlete_id", "anchor_date"]].drop_duplicates()),
                    "target_rows": within["id"].nunique(),
                    "athletes": within["athlete_id"].nunique(),
                })
    metadata = data.tests[["id", "name", "unit"]].rename(
        columns={"id": "test_id", "name": "test_name", "unit": "test_unit"}
    )
    coverage = pd.DataFrame(coverage_rows).merge(metadata, how="left", on="test_id")
    windows = pd.DataFrame(window_rows).merge(metadata, how="left", on="test_id")
    return SprintReadiness(classifications, years, coverage, windows)
