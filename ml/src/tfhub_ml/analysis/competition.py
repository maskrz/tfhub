"""Descriptive competition audit; no event-to-test equivalence is assumed."""

from dataclasses import dataclass

import pandas as pd

from tfhub_ml.data.extract import AuditData, CompetitionData


@dataclass(frozen=True)
class CompetitionAudit:
    overview: pd.DataFrame
    events: pd.DataFrame
    seasons: pd.DataFrame
    monthly: pd.DataFrame
    quality: pd.DataFrame
    overlap: pd.DataFrame
    gaps: pd.DataFrame
    gap_summary: pd.DataFrame
    windows: pd.DataFrame


def competition_audit(
    tests: AuditData, competition: CompetitionData, windows_days: tuple[int, ...]
) -> CompetitionAudit:
    results = competition.results.copy()
    results["result_date"] = pd.to_datetime(results["result_date"])
    results["numeric_value"] = pd.to_numeric(results["numeric_value"], errors="coerce")
    overview = pd.DataFrame([{
        "result_count": len(results),
        "event_count": len(competition.events),
        "events_with_results": results["event_id"].nunique(),
        "athletes_with_results": results["athlete_id"].nunique(),
        "athletes_with_test_and_competition_results": len(
            set(results["athlete_id"]) & set(tests.results["athlete_id"])
        ),
        "earliest_result_date": results["result_date"].min(),
        "latest_result_date": results["result_date"].max(),
        "missing_numeric_values": results["numeric_value"].isna().sum(),
    }])

    summaries = results.groupby("event_id").agg(
        result_count=("id", "size"),
        athlete_count=("athlete_id", "nunique"),
        earliest_result_date=("result_date", "min"),
        latest_result_date=("result_date", "max"),
        missing_numeric_values=("numeric_value", lambda values: values.isna().sum()),
    ).reset_index()
    events = competition.events.rename(columns={
        "id": "event_id", "name": "event_name", "unit": "current_event_unit",
        "active": "event_active",
    }).merge(summaries, on="event_id", how="left")
    for column in ("result_count", "athlete_count", "missing_numeric_values"):
        events[column] = events[column].fillna(0).astype(int)
    events = events.sort_values(["result_count", "event_id"], ascending=[False, True])
    distinct_dates = results.drop_duplicates(["event_id", "athlete_id", "result_date"])
    sequences = distinct_dates.groupby(["event_id", "athlete_id"]).size().groupby(level="event_id").agg(
        athletes_with_2plus_dates=lambda counts: (counts >= 2).sum(),
        athletes_with_3plus_dates=lambda counts: (counts >= 3).sum(),
    ).reset_index()
    events = events.merge(sequences, on="event_id", how="left")
    for column in ("athletes_with_2plus_dates", "athletes_with_3plus_dates"):
        events[column] = events[column].fillna(0).astype(int)

    seasons = results.groupby(["event_id", "season_type", "unit"], dropna=False).agg(
        result_count=("id", "size"),
        athlete_count=("athlete_id", "nunique"),
        missing_numeric_values=("numeric_value", lambda values: values.isna().sum()),
    ).reset_index()
    monthly = results.assign(month=results["result_date"].dt.to_period("M").astype(str)).groupby(
        "month", dropna=False
    ).agg(result_count=("id", "size"), athlete_count=("athlete_id", "nunique")).reset_index()

    quality_columns = ["issue_type", "result_id", "athlete_id", "event_id", "result_date", "unit", "season_type", "note"]
    quality_rows: list[dict[str, object]] = []
    events_by_id = competition.events.set_index("id")
    athlete_ids = set(tests.athletes["id"])
    for result in results.to_dict("records"):
        context = {key: result[key] for key in ("athlete_id", "event_id", "result_date", "unit", "season_type")}
        context["result_id"] = result["id"]
        def flag(kind: str, note: str) -> None:
            quality_rows.append({"issue_type": kind, **context, "note": note})
        if pd.isna(result["numeric_value"]):
            flag("missing_numeric_value", "Optional numeric value is absent; the original mark may represent a non-numeric result. Review at source.")
        elif result["numeric_value"] <= 0:
            flag("non_positive_numeric_value", "Review the original mark and event context; not automatically classified as invalid.")
        if result["athlete_id"] not in athlete_ids:
            flag("missing_athlete_reference", "Athlete not found in the extracted athlete table.")
        if result["event_id"] not in events_by_id.index:
            flag("missing_event_reference", "Event not found in the extracted event table.")
        elif result["unit"] != events_by_id.loc[result["event_id"], "unit"]:
            flag("unit_differs_from_current_event", "Result unit is a historical snapshot; difference from current event unit may be legitimate.")
        if result["season_type"] == "UNKNOWN":
            flag("unknown_season_type", "Indoor/outdoor classification is unknown; review before comparison.")
    quality = pd.DataFrame(quality_rows, columns=quality_columns)

    test_results = tests.results.rename(columns={
        "id": "test_result_id", "result_date": "test_date", "value": "test_value",
    })[["test_result_id", "athlete_id", "test_id", "test_date", "test_value"]].copy()
    test_results["test_date"] = pd.to_datetime(test_results["test_date"])
    comp_results = results.rename(columns={
        "id": "competition_result_id", "result_date": "competition_date",
        "unit": "competition_unit",
    })[["competition_result_id", "athlete_id", "event_id", "competition_date", "numeric_value", "competition_unit", "season_type"]]
    pairs = test_results.merge(comp_results, on="athlete_id", how="inner")
    pairs["gap_days"] = (pairs["competition_date"] - pairs["test_date"]).dt.days
    pairs["absolute_gap_days"] = pairs["gap_days"].abs()
    pairs["numeric_pair"] = pairs["numeric_value"].notna() & pd.to_numeric(pairs["test_value"], errors="coerce").notna()
    ordered = pairs.sort_values(
        ["test_id", "event_id", "athlete_id", "absolute_gap_days", "test_date", "competition_date", "test_result_id", "competition_result_id"]
    )
    nearest = ordered.drop_duplicates(["test_id", "event_id", "athlete_id"])
    gaps = nearest[[
        "test_id", "event_id", "athlete_id", "test_result_id", "competition_result_id",
        "test_date", "competition_date", "gap_days", "absolute_gap_days", "numeric_pair",
        "competition_unit", "season_type",
    ]].reset_index(drop=True)
    gap_summary = gaps.groupby(["test_id", "event_id"]).agg(
        athletes=("athlete_id", "nunique"),
        minimum_days=("absolute_gap_days", "min"),
        median_days=("absolute_gap_days", "median"),
        maximum_days=("absolute_gap_days", "max"),
    ).reset_index()
    overlap = pairs.groupby(["test_id", "event_id"]).agg(
        shared_athletes=("athlete_id", "nunique"), all_result_pairs=("athlete_id", "size")
    ).reset_index().sort_values(["shared_athletes", "test_id", "event_id"], ascending=[False, True, True])
    window_rows: list[dict[str, object]] = []
    for (test_id, event_id), group in pairs.groupby(["test_id", "event_id"]):
        for days in windows_days:
            close = group.loc[group["absolute_gap_days"] <= days]
            prior = group.loc[(group["gap_days"] >= 0) & (group["gap_days"] <= days)]
            numeric_close = close.loc[close["numeric_pair"]]
            window_rows.append({
                "test_id": test_id, "event_id": event_id, "window_days": days,
                "pairs_within_window": len(close),
                "athletes_within_window": close["athlete_id"].nunique(),
                "numeric_pair_athletes_within_window": numeric_close["athlete_id"].nunique(),
                "test_on_or_before_competition_pairs": len(prior),
                "test_on_or_before_competition_athletes": prior["athlete_id"].nunique(),
            })
    windows = pd.DataFrame(window_rows, columns=[
        "test_id", "event_id", "window_days", "pairs_within_window",
        "athletes_within_window", "numeric_pair_athletes_within_window",
        "test_on_or_before_competition_pairs", "test_on_or_before_competition_athletes",
    ])
    test_metadata = tests.tests[["id", "name", "unit", "category", "higher_is_better"]].rename(columns={
        "id": "test_id", "name": "test_name", "unit": "test_unit",
        "category": "test_category", "higher_is_better": "test_higher_is_better",
    })
    event_metadata = competition.events[["id", "name", "unit"]].rename(columns={
        "id": "event_id", "name": "event_name", "unit": "current_event_unit",
    })
    overlap = overlap.merge(test_metadata, on="test_id", how="left").merge(
        event_metadata, on="event_id", how="left"
    )
    windows = windows.merge(test_metadata, on="test_id", how="left").merge(
        event_metadata, on="event_id", how="left"
    )
    return CompetitionAudit(overview, events, seasons, monthly, quality, overlap, gaps, gap_summary, windows)
