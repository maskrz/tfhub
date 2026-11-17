"""Run the read-only TFHUB PostgreSQL data audit."""

from __future__ import annotations

import logging
import sys
from pathlib import Path

from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.audit import (
    athlete_statistics,
    coverage_analysis,
    cross_test_analysis,
    data_quality_issues,
    temporal_statistics,
    test_statistics,
)
from tfhub_ml.analysis.competition import competition_audit
from tfhub_ml.config.settings import load_settings
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.reporting.generate import generate_report


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()

        tests = test_statistics(data)
        athletes = athlete_statistics(data)
        coverage, coverage_distribution = coverage_analysis(data)
        monthly, yearly, athlete_period, repeat_intervals = temporal_statistics(data)
        gaps, windows, correlations = cross_test_analysis(
            data, settings.match_windows_days
        )
        issues = data_quality_issues(data)
        competition_results = competition_audit(data, competition, settings.match_windows_days)
        output = generate_report(
            data,
            PROJECT_ROOT / "reports",
            tests,
            athletes,
            coverage,
            coverage_distribution,
            monthly,
            yearly,
            athlete_period,
            repeat_intervals,
            gaps,
            windows,
            correlations,
            issues,
            competition_results,
        )
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1

    logging.info("Read-only audit complete. Report: %s", output / "data_audit_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
