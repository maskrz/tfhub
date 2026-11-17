from datetime import date

import pandas as pd
import pytest

from tfhub_ml.analysis.season_feasibility import assess_seasons, select_best_latest_attempt
from tfhub_ml.data.extract import AuditData, CompetitionData


def fixtures() -> tuple[AuditData, CompetitionData]:
    athletes = pd.DataFrame([{"id": 1}, {"id": 2}, {"id": 3}])
    tests = pd.DataFrame([
        {"id": 10, "name": "Sprint", "unit": "s", "higher_is_better": False},
        {"id": 20, "name": "Jump", "unit": "m", "higher_is_better": True},
    ])
    results = pd.DataFrame([
        {"id": 1, "athlete_id": 1, "test_id": 10, "result_date": "2023-12-01", "value": 8.0},
        {"id": 2, "athlete_id": 1, "test_id": 20, "result_date": "2024-04-30", "value": 2.0},
        {"id": 3, "athlete_id": 1, "test_id": 20, "result_date": "2024-04-30", "value": 2.3},
        {"id": 4, "athlete_id": 1, "test_id": 10, "result_date": "2024-05-01", "value": 7.0},
        {"id": 5, "athlete_id": 2, "test_id": 10, "result_date": "2024-04-01", "value": 9.0},
        {"id": 6, "athlete_id": 3, "test_id": 10, "result_date": "2024-04-01", "value": 9.0},
    ])
    events = pd.DataFrame([{"id": 4, "name": "60 m", "unit": "s"}])
    competitions = pd.DataFrame([
        {"id": 10, "athlete_id": 1, "event_id": 4, "result_date": "2024-05-01", "numeric_value": 8.0, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 11, "athlete_id": 1, "event_id": 4, "result_date": "2024-10-31", "numeric_value": 7.9, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 12, "athlete_id": 1, "event_id": 4, "result_date": "2024-11-01", "numeric_value": 6.0, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 13, "athlete_id": 2, "event_id": 4, "result_date": "2024-06-01", "numeric_value": 9.0, "unit": "s", "season_type": "UNKNOWN"},
        {"id": 14, "athlete_id": 3, "event_id": 4, "result_date": "2024-07-01", "numeric_value": None, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 15, "athlete_id": 3, "event_id": 4, "result_date": "2024-07-02", "numeric_value": 9.0, "unit": "s", "season_type": "OUTDOOR"},
        {"id": 16, "athlete_id": 1, "event_id": 4, "result_date": "2025-06-01", "numeric_value": 7.8, "unit": "s", "season_type": "OUTDOOR"},
    ])
    return AuditData(athletes, tests, results, 0), CompetitionData(events, competitions)


def test_best_attempt_on_latest_date_respects_direction() -> None:
    history = pd.DataFrame([
        {"id": 1, "result_date": pd.Timestamp("2024-01-01"), "value": 1.0},
        {"id": 2, "result_date": pd.Timestamp("2024-02-01"), "value": 2.0},
        {"id": 3, "result_date": pd.Timestamp("2024-02-01"), "value": 3.0},
        {"id": 4, "result_date": pd.Timestamp("2024-02-01"), "value": 3.0},
    ])
    assert select_best_latest_attempt(history, True)["id"] == 3
    assert select_best_latest_attempt(history, False)["id"] == 2


def test_feasibility_excludes_unknown_bad_marks_and_incomplete_season() -> None:
    data, competition = fixtures()
    audit = assess_seasons(data, competition, 4, date(2025, 6, 30))
    seasons = audit.seasons.set_index("season_year")
    assert seasons.loc[2024, "eligible_athlete_seasons"] == 1
    assert seasons.loc[2024, "unknown_season_result_count"] == 1
    assert seasons.loc[2024, "athletes_with_outdoor_records_requiring_review"] == 1
    assert not seasons.loc[2025, "season_complete"]
    assert seasons.loc[2025, "eligible_athlete_seasons"] == 0
    assert audit.distinct_eligible_athletes == 1
    coverage = audit.test_coverage.set_index(["season_year", "cohort", "test_id"])
    assert coverage.loc[(2024, "eligible", 10), "with_result"] == 1
    assert coverage.loc[(2024, "eligible", 10), "median_age_days"] == 151
    assert coverage.loc[(2024, "eligible", 20), "multiple_attempts_same_day"] == 1
    assert coverage.loc[(2024, "all_with_prior_test", 10), "with_result"] == 3
    assert audit.input_ages.set_index(["season_year", "cohort"]).loc[(2024, "eligible"), "median_days"] == 1


def test_repeated_athlete_seasons_are_not_counted_as_distinct_athletes() -> None:
    data, competition = fixtures()
    audit = assess_seasons(data, competition, 4, date(2025, 11, 1))
    assert audit.seasons["eligible_athlete_seasons"].sum() == 2
    assert audit.distinct_eligible_athletes == 1


def test_rejects_wrong_event_or_unit() -> None:
    data, competition = fixtures()
    with pytest.raises(ValueError, match="verified"):
        assess_seasons(data, competition, 5, date(2025, 11, 1))
    competition.events.loc[0, "unit"] = "m"
    with pytest.raises(ValueError, match="seconds"):
        assess_seasons(data, competition, 4, date(2025, 11, 1))


def test_zero_eligible_candidates_have_clear_empty_age_summary() -> None:
    data, competition = fixtures()
    audit = assess_seasons(data, competition, 4, date(2024, 6, 1))
    assert audit.seasons["eligible_athlete_seasons"].sum() == 0
    assert "eligible" not in set(audit.input_ages["cohort"])
    eligible = audit.test_coverage.loc[audit.test_coverage["cohort"] == "eligible"]
    assert (eligible["with_result"] == 0).all()


def test_unknown_values_and_earlier_year_inputs_remain_explicit() -> None:
    data, competition = fixtures()
    audit = assess_seasons(data, competition, 4, date(2025, 11, 1))
    season = audit.seasons.set_index("season_year").loc[2025]
    assert season["athletes_with_prior_test"] == 3
    coverage = audit.test_coverage.set_index(["season_year", "cohort", "test_id"])
    assert coverage.loc[(2025, "eligible", 20), "median_age_days"] == 1


def test_future_audit_date_is_not_treated_as_complete() -> None:
    data, competition = fixtures()
    with pytest.raises(ValueError, match="future"):
        assess_seasons(data, competition, 4, date(9999, 1, 1))


def test_confirmed_2026_unknown_ids_only_are_treated_as_outdoor() -> None:
    data, competition = fixtures()
    extra = pd.DataFrame([
        {"id": 20, "athlete_id": 1, "event_id": 4, "result_date": "2026-05-01", "numeric_value": 7.7, "unit": "s", "season_type": "UNKNOWN"},
        {"id": 21, "athlete_id": 2, "event_id": 4, "result_date": "2026-10-31", "numeric_value": 8.5, "unit": "s", "season_type": "UNKNOWN"},
        {"id": 22, "athlete_id": 3, "event_id": 4, "result_date": "2026-04-30", "numeric_value": 8.0, "unit": "s", "season_type": "UNKNOWN"},
    ])
    competition = CompetitionData(
        competition.events, pd.concat([competition.results, extra], ignore_index=True)
    )
    as_of = date(2026, 11, 1)
    unconfirmed = assess_seasons(data, competition, 4, as_of)
    confirmed = assess_seasons(data, competition, 4, as_of, frozenset({20}))
    year = confirmed.seasons.set_index("season_year").loc[2026]
    assert unconfirmed.seasons.set_index("season_year").loc[2026, "eligible_athlete_seasons"] == 0
    assert year["unknown_season_result_count"] == 2
    assert year["confirmed_unknown_as_outdoor_result_count"] == 1
    assert year["athletes_with_recorded_outdoor_60m"] == 0
    assert year["eligible_athlete_seasons"] == 1
    assert confirmed.distinct_eligible_athletes == 1
    assert competition.results.loc[competition.results["id"] == 20, "season_type"].iloc[0] == "UNKNOWN"
    with pytest.raises(ValueError, match="UNKNOWN results"):
        assess_seasons(data, competition, 4, as_of, frozenset({22}))
    with pytest.raises(ValueError, match="UNKNOWN results"):
        assess_seasons(data, competition, 4, as_of, frozenset({10}))
