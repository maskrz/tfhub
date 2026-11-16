"""Environment-backed settings for the read-only audit."""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

from dotenv import load_dotenv

_SCHEMA_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    db_schema: str = "public"
    match_windows_days: tuple[int, ...] = (7, 14, 30, 60)


def load_settings(
    env_file: Path | None = None,
    environ: Mapping[str, str] | None = None,
) -> Settings:
    """Load required database settings; never silently supply credentials."""
    if environ is None:
        load_dotenv(env_file or Path(".env"), override=False)
        values = os.environ
    else:
        values = environ

    required = ("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")
    missing = [key for key in required if not values.get(key, "").strip()]
    if missing:
        raise ValueError(
            "Missing required database settings: "
            + ", ".join(missing)
            + ". Copy .env.example to .env and fill in the read-only connection values."
        )

    try:
        port = int(values["DB_PORT"])
    except ValueError as exc:
        raise ValueError("DB_PORT must be an integer between 1 and 65535.") from exc
    if not 1 <= port <= 65535:
        raise ValueError("DB_PORT must be an integer between 1 and 65535.")

    schema = values.get("DB_SCHEMA", "public").strip()
    if not _SCHEMA_PATTERN.fullmatch(schema):
        raise ValueError("DB_SCHEMA must be a simple PostgreSQL identifier.")

    try:
        match_windows = tuple(
            int(value.strip())
            for value in values.get("MATCH_WINDOWS_DAYS", "7,14,30,60").split(",")
        )
    except ValueError as exc:
        raise ValueError("MATCH_WINDOWS_DAYS must be a comma-separated list of positive integers.") from exc
    if not match_windows or any(days <= 0 for days in match_windows):
        raise ValueError("MATCH_WINDOWS_DAYS must contain positive day counts.")
    if len(set(match_windows)) != len(match_windows):
        raise ValueError("MATCH_WINDOWS_DAYS must not contain duplicates.")

    return Settings(
        db_host=values["DB_HOST"].strip(),
        db_port=port,
        db_name=values["DB_NAME"].strip(),
        db_user=values["DB_USER"].strip(),
        db_password=values["DB_PASSWORD"],
        db_schema=schema,
        match_windows_days=match_windows,
    )
