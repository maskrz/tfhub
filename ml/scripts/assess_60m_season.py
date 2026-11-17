"""Assess availability of test inputs and subsequent outdoor 60 m targets."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.season_feasibility import assess_seasons
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True, help="Verified 60 m competition event ID")
    parser.add_argument(
        "--as-of", type=date.fromisoformat, default=date.today(),
        help="Audit date in YYYY-MM-DD format (default: today)",
    )
    parser.add_argument(
        "--confirmed-unknown-outdoor-ids",
        type=Path,
        help="CSV containing explicitly confirmed 2026 UNKNOWN result IDs in a result_id column",
    )
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()
        confirmed_ids: frozenset[int] = frozenset()
        if args.confirmed_unknown_outdoor_ids:
            confirmed_ids = load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
        audit = assess_seasons(data, competition, args.event_id, args.as_of, confirmed_ids)
        output = PROJECT_ROOT / "reports" / (
            "season_feasibility_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        )
        output.mkdir(parents=True, exist_ok=False)
        audit.seasons.to_csv(output / "seasons.csv", index=False)
        audit.test_coverage.to_csv(output / "test_coverage.csv", index=False)
        audit.input_ages.to_csv(output / "prediction_date_ages.csv", index=False)
        if confirmed_ids:
            pd.DataFrame({"result_id": sorted(confirmed_ids)}).to_csv(
                output / "confirmed_outdoor_result_ids.csv", index=False
            )
        eligible = int(audit.seasons["eligible_athlete_seasons"].sum())
        visible_coverage = audit.test_coverage.loc[
            audit.test_coverage["cohort_athlete_seasons"] > 0
        ].sort_values(["season_year", "with_result", "test_id"], ascending=[False, False, True])
        text = [
            "# Outdoor 60 m season feasibility audit",
            "",
            f"- Snapshot audit date: {args.as_of}; competition event ID: {args.event_id} (60 m).",
            "- Seasons covered: from the year of the earliest available 60 m competition result to the audit year; every current athlete is considered for each year. This is an explicit reporting population, not historical roster membership.",
            "- Prediction date: last test of any kind before May 1, including prior years. Latest available result per test is selected on or before that date; tied-date attempts use the best result according to the test's current direction. Input ages are measured from the prediction date.",
            "- Target: lowest positive numeric time in seconds, from May 1 through October 31, recorded as OUTDOOR or explicitly confirmed below. INDOOR and other UNKNOWN results are excluded; unknown classifications remain counted for review.",
            f"- Explicit user confirmation: {len(confirmed_ids)} identified UNKNOWN 60 m results from May–October 2026 are treated as outdoor only for this analysis. "
            + ("Their IDs are recorded in `confirmed_outdoor_result_ids.csv`." if confirmed_ids else "No UNKNOWN results were confirmed for this run."),
            "- A candidate is counted only in a completed season with at least one prior test, an eligible outdoor 60 m result, and no outdoor 60 m mark missing a numeric value, nonpositive, or in a different unit. Such marks require review because they could affect the season best. Season completeness is calendar-based; missing competition records cannot be detected.",
            "- Source dates are event dates, not record creation or correction timestamps. Current test metadata and today's data snapshot cannot prove what was actually known on a historical prediction date. Historical target completeness and availability must be verified before modelling.",
            "- No models were trained; no athlete names, individual measurements, or candidate labels are exported.",
            "",
            "## Season coverage",
            "",
            f"Eligible athlete-seasons: **{eligible}**, from **{audit.distinct_eligible_athletes} distinct athletes**. Athlete-seasons are repeated observations, not independent athletes.",
            "",
            _markdown_table(audit.seasons, limit=len(audit.seasons)),
            "",
            "Counts are not mutually exclusive unless stated. Raw UNKNOWN counts include confirmed records; `confirmed_unknown_as_outdoor_result_count` shows those used for this run. Other UNKNOWN records might affect the actual season best.",
            "",
            "## Prediction-date age",
            "",
            "Days between the last pre-May test and May 1. Cohorts show all athlete-seasons with a prior test and the eligible subset.",
            "",
            _markdown_table(audit.input_ages, limit=len(audit.input_ages)),
            "",
            "## Input test coverage",
            "",
            "Per season, cohort and test, `with_result` and `missing_result` count athlete-seasons; input age is measured from the test result to the prediction date. The all-with-prior-test cohort is descriptive and includes athletes with no verified target. The full table is in `test_coverage.csv`.",
            "",
            _markdown_table(visible_coverage, limit=30),
            "",
            "## Assessment and next steps",
            "",
            "These counts measure availability, not evidence that a model can predict accurately. Review athlete-season coverage, stale measurements, UNKNOWN season flags and the completeness and historical availability of results with the coach. Do not select inputs or train a model until the target and evaluation are validated. Any later evaluation must prevent leakage and account for repeated athlete-seasons.",
            "",
        ]
        (output / "feasibility_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Feasibility report: %s", output / "feasibility_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
