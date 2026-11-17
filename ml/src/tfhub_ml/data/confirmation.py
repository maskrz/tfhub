"""Load a reviewed list of source result IDs."""

from pathlib import Path

import pandas as pd


def load_confirmed_outdoor_ids(path: Path) -> frozenset[int]:
    table = pd.read_csv(path, dtype={"result_id": "string"})
    if list(table.columns) != ["result_id"] or table.empty:
        raise ValueError("Confirmation CSV must contain a nonempty result_id column only.")
    if table["result_id"].isna().any() or not table["result_id"].str.fullmatch(r"[0-9]+").all():
        raise ValueError("Confirmation result IDs must be positive integers.")
    ids = frozenset(int(value) for value in table["result_id"])
    if 0 in ids or len(ids) != len(table):
        raise ValueError("Confirmation result IDs must be positive and unique.")
    return ids
