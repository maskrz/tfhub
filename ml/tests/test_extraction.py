import pandas as pd
from sqlalchemy import create_engine, text

from tfhub_ml.data.extract import extract_from_connection


def test_extraction_reads_only_audit_tables_from_attached_schema() -> None:
    engine = create_engine("sqlite://")
    with engine.connect() as connection:
        connection.execute(text("ATTACH DATABASE ':memory:' AS audit"))
        connection.execute(
            text(
                "CREATE TABLE audit.athletes "
                "(id INTEGER, birth_year INTEGER, gender TEXT, active INTEGER, training_group_id INTEGER)"
            )
        )
        connection.execute(
            text(
                "CREATE TABLE audit.tests "
                "(id INTEGER, name TEXT, category TEXT, unit TEXT, higher_is_better INTEGER, active INTEGER)"
            )
        )
        connection.execute(
            text(
                "CREATE TABLE audit.test_results "
                "(id INTEGER, athlete_id INTEGER, test_id INTEGER, result_date TEXT, value REAL, type TEXT)"
            )
        )
        connection.execute(text("CREATE TABLE audit.training_groups (id INTEGER)"))
        connection.execute(
            text(
                "INSERT INTO audit.athletes VALUES "
                "(1, 2000, 'MALE', 1, NULL)"
            )
        )
        connection.execute(
            text("INSERT INTO audit.tests VALUES (2, 'Jump', 'DISTANCE', 'm', 1, 1)")
        )
        connection.execute(
            text(
                "INSERT INTO audit.test_results VALUES "
                "(3, 1, 2, '2025-01-01', 2.5, 'TRAINING')"
            )
        )
        connection.execute(text("INSERT INTO audit.training_groups VALUES (4)"))
        connection.commit()

        data = extract_from_connection(connection, "audit")

    assert data.athletes["id"].tolist() == [1]
    assert data.tests["name"].tolist() == ["Jump"]
    assert data.results["id"].tolist() == [3]
    assert data.training_group_count == 1
    engine.dispose()
