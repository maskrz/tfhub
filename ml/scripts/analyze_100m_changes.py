"""Explore within-athlete test changes against 100 m and season-best outcomes."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.within_athlete import analyze
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
        audit = analyze(data, competition, args.event_id, args.as_of, confirmed, 30)
        output = PROJECT_ROOT / "reports" / ("100m_changes_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        audit.coverage.to_csv(output / "coverage.csv", index=False)
        audit.correlations.to_csv(output / "correlations.csv", index=False, float_format="%.6f")
        pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        changes = audit.correlations.loc[audit.correlations["analysis"] == "within_athlete_change"]
        season_options = audit.correlations.loc[
            (audit.correlations["outcome"] == "season_best") & (audit.correlations["analysis"] != "within_athlete_change")
        ]
        text = [
            "# Within-athlete tests and outdoor 100 m outcomes", "",
            f"- Snapshot cutoff {args.as_of}; verified event ID {args.event_id}; {len(confirmed)} reviewed 100 m result IDs are copied into this directory. The source database is not changed.",
            "- Each test date generates one research prediction date, the following calendar day. Same-day test attempts are combined using the best value according to current test direction. Invalid/nonpositive attempts on that date exclude the anchor. Each test is analyzed separately: missing other tests do not exclude an observation.",
            "- Near-term outcome: fastest comparable recorded outdoor 100 m race on days 1–30 after prediction; complete windows only. Unconfirmed UNKNOWN or noncomparable outdoor results in a window require review. No observed race is not evidence of poor performance.",
            "- Season outcome: fastest recorded outdoor 100 m race from May 1–October 31 of the prediction year, including prior season results known before the prediction date and later races. Calendar-complete seasons only. Unconfirmed UNKNOWN, noncomparable outdoor marks or an outdoor race on prediction day require review. Races on the prediction day cannot be assigned a reliable ordering.",
            "- Season research dates are generated after tests in the same calendar year (or on December 31 for January 1) and before October 31. Preseason and in-season options compare earliest and latest eligible test date per athlete-season, separately. Other pre-season dates and earlier-year tests are not evaluated by these options. This is exploratory sampling, not an agreed operational policy.",
            "- Level correlations compare test values and observed times; these mainly mix between-athlete and within-athlete differences. For `within_athlete_change`, each test is paired with the latest earlier eligible test whose full outcome window ended strictly before the newer test date. Seasonal changes therefore compare different completed seasons, not repeated labels from the same season.",
            "- Changes are current minus previous test and current minus previous time. Negative correlation can mean improving a higher-is-better test accompanies faster times. Pearson and Spearman are pooled over changes; repeated athletes and reused observations are not independent. Blank means fewer than two pairs or constant values; even nonblank values may be unreliable in tiny samples.",
            "- Known-best coverage does not mean a future race occurred. A season-best target can already be achieved at prediction time, so in-season level correlations are not proof of forecasting ability. See `observed_without_future_race`. Earliest/latest options condition on observed outcomes; they are descriptive, not model evaluations.",
            "- Wind and competition context are unadjusted. Record entry/correction dates are unavailable; event dates do not prove historical availability. Missing race imports and selective participation can affect outcomes. No test-age cutoff or age-based extrapolation is imposed.",
            "- Samples are small, multiple tests are examined and results may be driven by athlete differences or changes in age/training rather than test effects. No causal claim, individual prediction, prediction interval or trained model is produced.",
            "", "## Coverage by test", "", _markdown_table(audit.coverage, limit=len(audit.coverage)),
            "", "## Within-athlete changes", "", _markdown_table(changes.round(4), limit=len(changes)),
            "", "## Season test-date options and level correlations", "", _markdown_table(season_options.round(4), limit=len(season_options)),
            "", "Full results, including near-term level correlations, are in `correlations.csv`.", "",
        ]
        (output / "change_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, OSError, SQLAlchemyError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("Within-athlete report: %s", output / "change_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
