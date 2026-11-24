"""Compare existing baseline cases with an approximate wind adjustment."""

import argparse
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.wind_review import assess_wind
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-review", type=Path, required=True)
    parser.add_argument("--event-id", type=int, required=True)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        review = pd.read_csv(args.baseline_review, dtype={
            "athlete_id": "int64", "previous_result_id": "int64", "target_result_id": "int64",
        })
        confirmed = load_confirmed_outdoor_ids(args.baseline_review.parent / "confirmed_outdoor_result_ids.csv")
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            with engine.connect() as connection:
                with connection.begin():
                    connection.execute(text("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY"))
                    schema = f'"{settings.db_schema}"'
                    events = pd.read_sql_query(text(f"SELECT id, name, unit FROM {schema}.competition_events WHERE id = :event_id"), connection, params={"event_id": args.event_id})
                    if len(events) != 1 or str(events.iloc[0]["name"]).strip().casefold() != "100 m" or events.iloc[0]["unit"] != "s":
                        raise ValueError("The chosen event must identify 100 m in seconds.")
                    races = pd.read_sql_query(text(
                        f"SELECT id, result_date, numeric_value, unit, season_type, wind FROM {schema}.competition_results WHERE event_id = :event_id"
                    ), connection, params={"event_id": args.event_id})
        finally:
            engine.dispose()
        result = assess_wind(review, races, confirmed)
        output = PROJECT_ROOT / "reports" / ("100m_wind_review_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        for name, table in (("coverage", result.coverage), ("metrics", result.metrics), ("wind_change", result.wind_change)):
            table.to_csv(output / f"{name}.csv", index=False, float_format="%.6f")
        report = [
            "# 100 m baseline wind review", "",
            f"- Baseline cases: `{args.baseline_review.name}` from `{args.baseline_review.parent.name}`; event ID {args.event_id}. This review uses the earlier study's fixed cases and checks race IDs, dates, times and units against the current read-only database snapshot.",
            "- Coach-provided exploratory rule: adjusted time = recorded time + wind × (0.05 × recorded time / 10), with wind in m/s (positive tailwind). This is an approximation, not a validated physical correction or a claim of causality.",
            "- Both the prior race and its future target race are adjusted to approximate calm air. The raw and adjusted comparisons use exactly the same cases with both winds present; missing/nonfinite winds are excluded and counted. No rounding is applied before aggregation.",
            "- Future race wind is only known retrospectively, so the adjusted error is an oracle-assisted diagnostic, NOT the accuracy of a real-time forecast of a raw race time. It does not evaluate the 60 m model.",
            "- Targets remain the original fastest recorded race within each 30-day window; selecting the fastest adjusted race could give different results. Both estimates and outcomes are standardized, so the metrics answer a different question from the original forecast.",
            "- Signed error = prior time minus future time. A positive wind change means a more favorable tailwind for the target. Repeated windows can reuse races and athletes; cases are not independent. Athlete-weighted MAE averages each athlete's mean absolute error equally.",
            "- Wind values and classifications can have changed since the earlier study. There are no historical record-entry timestamps. Weather, timing methods and other factors are not controlled. No prediction intervals or causal conclusions are justified.",
            "", "## Coverage", "", _markdown_table(result.coverage),
            "", "## Recorded versus approximate calm-air comparison", "", _markdown_table(result.metrics.round(4), limit=len(result.metrics)),
            "", "## Wind changes", "", _markdown_table(result.wind_change.round(4), limit=len(result.wind_change)),
            "", "This report contains aggregate data only. It does not modify production or save athlete-level results.", "",
        ]
        (output / "wind_report.md").write_text("\n".join(report), encoding="utf-8")
    except (ValueError, KeyError, OSError, SQLAlchemyError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Wind report: %s", output / "wind_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
