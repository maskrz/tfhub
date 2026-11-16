"""Extract only the existing athletic-test data needed by the audit."""

from dataclasses import dataclass

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Connection, Engine

from tfhub_ml.config.settings import Settings


@dataclass(frozen=True)
class AuditData:
    athletes: pd.DataFrame
    tests: pd.DataFrame
    results: pd.DataFrame
    training_group_count: int


def read_audit_data(engine: Engine, settings: Settings) -> AuditData:
    """Read one consistent snapshot; the transaction is explicitly read-only."""
    schema = f'"{settings.db_schema}"'
    with engine.connect() as connection:
        with connection.begin():
            connection.execute(text("SET TRANSACTION READ ONLY"))
            return extract_from_connection(connection, schema)


def extract_from_connection(connection: Connection, schema: str) -> AuditData:
    athletes = pd.read_sql_query(
        text(
            f"SELECT id, birth_year, gender, active, training_group_id "
            f"FROM {schema}.athletes ORDER BY id"
        ),
        connection,
    )
    tests = pd.read_sql_query(
        text(
            f"SELECT id, name, category, unit, higher_is_better, active "
            f"FROM {schema}.tests ORDER BY id"
        ),
        connection,
    )
    results = pd.read_sql_query(
        text(
            f"SELECT id, athlete_id, test_id, result_date, value, type "
            f"FROM {schema}.test_results ORDER BY id"
        ),
        connection,
    )
    training_group_count = int(
        connection.execute(
            text(f"SELECT count(*) FROM {schema}.training_groups")
        ).scalar_one()
    )
    return AuditData(athletes, tests, results, training_group_count)
