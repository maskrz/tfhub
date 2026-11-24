"""Compare a previous-race baseline to models adding athletic tests."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.multi_test_study import run_comparison
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
        result = run_comparison(data, competition, args.event_id, args.as_of, confirmed, 30)
        output = PROJECT_ROOT / "reports" / ("100m_test_comparison_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        result.coverage.to_csv(output / "coverage.csv", index=False)
        result.metrics.to_csv(output / "metrics.csv", index=False, float_format="%.6f")
        pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        text = [
            "# Experimental comparison: previous 100 m and test inputs", "",
            f"- Snapshot cutoff {args.as_of}; event ID {args.event_id}; 30-day future window. {len(confirmed)} reviewed result IDs recorded separately.",
            "- Uses the earlier 100 m study's anchors and outcome: day after a test or 100 m race date; fastest recorded comparable outdoor 100 m race on days 1–30 after that date. Full windows only; unconfirmed UNKNOWN and noncomparable outdoor results require review.",
            "- All methods require a previous outdoor 100 m time; baseline repeats the fastest time from the latest prior race date. Each linear model uses that time plus one test, or all four tests (60 m spikes, 40 m shoes, standing long jump, backward 2 kg ball throw). Latest test strictly before prediction; best same-day attempt; no recency cutoff. Missing or invalid latest tests are not imputed.",
            "- Fits use ordinary least squares with an intercept, using only earlier completed outcome windows from other athletes. Each training fold needs more distinct athletes than coefficients, and a full-rank design. Features are standardized using that fold alone. Unevaluable folds are counted, not guessed.",
            "- Each model is compared against the baseline on the same evaluable cases in its named cohort. Individual cohorts differ across tests; only `all_models_common` is directly comparable across all methods. No `all_models_common` rows means no shared evaluable cases.",
            "- Repeated prediction dates and overlapping target windows are not independent; training and metrics may over-weight frequent athletes. Athlete-weighted MAE averages each athlete's mean absolute error. No-data windows are excluded, not treated as bad performances.",
            "- Small samples and selection among models can produce unstable results. Wind and race context are not adjusted; historical entry/correction dates are unavailable and missing competitions cannot be ruled out. No models or individual predictions are saved. Errors are not prediction intervals.",
            "", "## Coverage and skipped fits", "", _markdown_table(result.coverage, limit=len(result.coverage)),
            "", "## Matched evaluation errors (seconds)", "", _markdown_table(result.metrics.round(4), limit=len(result.metrics)),
            "", "Do not interpret a lower error as proof of generalization or causality. Cases in different cohorts cannot be directly ranked.", "",
        ]
        (output / "comparison_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Comparison report: %s", output / "comparison_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
