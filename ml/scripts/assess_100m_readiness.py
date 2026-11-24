"""Audit 100 m competition and prior measurement timing without training a model."""

import argparse
import logging
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.exc import SQLAlchemyError

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from tfhub_ml.analysis.sprint_readiness import assess_sprint_readiness
from tfhub_ml.config.settings import load_settings
from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids
from tfhub_ml.data.extract import read_audit_data
from tfhub_ml.database.connection import create_read_only_engine
from tfhub_ml.reporting.generate import _markdown_table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-id", type=int, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    confirmation = parser.add_mutually_exclusive_group()
    confirmation.add_argument("--confirmed-unknown-outdoor-ids", type=Path)
    confirmation.add_argument(
        "--confirm-current-unknown-outdoor", action="store_true",
        help="Explicitly confirm all selected event's UNKNOWN rows in this snapshot; records IDs in the report.",
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
        if args.confirm_current_unknown_outdoor:
            rows = competition.results
            dates = pd.to_datetime(rows["result_date"])
            confirmed = frozenset(rows.loc[
                (rows["event_id"] == args.event_id) & rows["season_type"].eq("UNKNOWN")
                & (dates <= pd.Timestamp(args.as_of)), "id"
            ].astype(int))
            if not confirmed:
                raise ValueError("No UNKNOWN 100 m results exist in the selected snapshot to confirm.")
        elif args.confirmed_unknown_outdoor_ids:
            confirmed = load_confirmed_outdoor_ids(args.confirmed_unknown_outdoor_ids)
        else:
            confirmed = frozenset()
        audit = assess_sprint_readiness(data, competition, args.event_id, args.as_of, settings.match_windows_days, confirmed)
        output = PROJECT_ROOT / "reports" / ("100m_readiness_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
        for name, table in (
            ("classifications", audit.classifications), ("years", audit.years),
            ("prior_coverage", audit.coverage), ("windows", audit.windows),
        ):
            table.to_csv(output / f"{name}.csv", index=False)
        if confirmed:
            pd.DataFrame({"result_id": sorted(confirmed)}).to_csv(output / "confirmed_outdoor_result_ids.csv", index=False)
        cohort = "OUTDOOR_WITH_CONFIRMED" if confirmed else "OUTDOOR"
        outdoor_coverage = audit.coverage.loc[audit.coverage["target_classification"] == cohort]
        outdoor_windows = audit.windows.loc[audit.windows["target_classification"] == cohort]
        text = [
            "# 100 m data-readiness audit", "",
            f"- Snapshot cutoff: {args.as_of}; verified event ID: {args.event_id}. Results after the cutoff are excluded.",
            "- Descriptive only: no model, prediction examples, or individual measurements exported. The study considers the current snapshot, not historical roster membership.",
            f"- Explicit confirmation: {len(confirmed)} selected UNKNOWN result IDs are treated as outdoor in the OUTDOOR_WITH_CONFIRMED cohort only; their IDs are saved in this report directory. Recorded classifications remain unchanged. Other UNKNOWN records are not assumed outdoor.",
            "- Comparable target rows have a positive finite numeric value in seconds. No wind, round, timing method, or competition-context filter is applied; wind was not read and needs separate review.",
            "- A prior source is a positive finite test result or a positive finite 100 m outdoor result in seconds for the same athlete. The combined cohort also uses confirmed UNKNOWN records as prior sources. Source dates must be strictly before the target date. Same-day sources are counted separately because ordering is unknown.",
            "- Windows compare existing measurement dates with future race rows at gaps of 1 through N calendar days, inclusive. Attempts on the same source date count as one athlete-source date; race rows remain separate, even on the same day. These are exploratory timing counts, not selected prediction dates, independent examples, or a chosen forecast horizon.",
            "- Only observed races can be counted. Race availability and attendance may bias evaluation. No entry/correction timestamps are available; event dates do not prove historical availability. Current test metadata can change, and competition records can be incomplete.",
            "", "## Recorded classifications", "", _markdown_table(audit.classifications, limit=len(audit.classifications)),
            "", "## Results by calendar year and classification", "", _markdown_table(audit.years, limit=len(audit.years)),
            f"", f"## Prior-source coverage for {cohort} targets", "",
            "Nearest prior gaps are in days per target row; each target may reuse an earlier source. The CSV also contains the original classifications.",
            "", _markdown_table(outdoor_coverage, limit=len(outdoor_coverage)),
            "", f"## Candidate date-gap windows for {cohort} targets", "",
            "Pair, date and result counts overlap; distinct athlete counts are not additive. See the CSV for all classifications.",
            "", _markdown_table(outdoor_windows, limit=len(outdoor_windows)),
            "", "These counts do not measure predictive accuracy or establish the target, selection policy, or statistical independence. Review unknown classifications, wind and context before designing a model.", "",
        ]
        (output / "readiness_report.md").write_text("\n".join(text), encoding="utf-8")
    except (ValueError, SQLAlchemyError, OSError) as exc:
        logging.error("%s", exc)
        return 1
    logging.info("100 m readiness report: %s", output / "readiness_report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
