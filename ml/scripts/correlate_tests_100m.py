"""Explore associations between athletic tests and subsequent outdoor 100 m times."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.test_race_correlations import correlate_tests
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--confirmed-unknown-outdoor-ids", type=Path, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    try:
        confirmed = load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
        settings = load_settings(PROJECT_ROOT / ".env")
        engine = create_read_only_engine(settings)
        try:
            data, competition = read_audit_data(engine, settings)
        finally:
            engine.dispose()
        coverage, correlations = correlate_tests(data, competition, args.event_id, args.as_of, confirmed, 30)
        output = PROJECT_ROOT / "reports" / ("100m_test_correlations_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        coverage.to_csv(output / "coverage.csv", index=False)
        correlations.to_csv(output / "correlations.csv", index=False, float_format="%.6f")
        pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        display = correlations.drop(columns=["warning", "unusable_latest_test_dates"])
        text = [
            "# Prior tests and future outdoor 100 m: descriptive correlations", "",
            f"- Snapshot cutoff {args.as_of}; verified event ID {args.event_id}; forecast window: days 1–30 after prediction date, inclusive.",
            f"- {len(confirmed)} previously reviewed UNKNOWN result IDs are copied into this directory. They must still identify this event and be UNKNOWN or OUTDOOR. Other UNKNOWN results are not treated as outdoor.",
            "- Prediction dates are the day after each distinct athlete's test or 100 m race date. Target is the fastest recorded comparable outdoor 100 m result in the following window; same-day races are excluded. Only fully elapsed windows count; unconfirmed UNKNOWN and noncomparable outdoor marks require review.",
            "- For each test separately, use its latest date strictly before the prediction date, with best same-day attempt according to current test direction. No test-age cutoff is imposed. Invalid or missing values on the latest date cause the test to be omitted rather than replaced by older data.",
            "- Each row reports Pearson or Spearman (average ranks) on paired prediction dates. Blank correlation means too few pairs or no variation. `distinct_test_results` and `distinct_target_races` show reuse; dates and targets may overlap and are not independent. Pairs from different tests need not be the same cohort.",
            "- All correlations are exploratory and sample size may be too small for reliable conclusions. Many tests and repeated athletes increase the chance of misleading patterns; pooled correlations can reflect differences between athletes rather than changes within an athlete. No significance or causal claim is made.",
            "- Race wind and conditions are not adjusted. The snapshot may include results entered or corrected after historical prediction dates; no entry or correction timestamps are available. No recorded race is not evidence of poor performance.",
            "- Negative correlation for a distance or power test means larger test values tend to accompany faster (lower) times, not proof the test predicts a result. Correlation is not validation of a prediction model. No model is trained and no individual measurements are exported.",
            "", "## Coverage", "", _markdown_table(coverage),
            "", "## Correlations by test", "", _markdown_table(display.round(4), limit=len(display)),
            "", "The full CSV includes the small-sample warning and invalid-latest-test counts.", "",
        ]
        (output / "correlation_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, OSError, SQLAlchemyError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Correlation report: %s", output / "correlation_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
