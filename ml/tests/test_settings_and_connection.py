import pytest

from tfhub_ml.config.settings import Settings, load_settings
from tfhub_ml.database.connection import build_database_url, create_read_only_engine


def test_loads_required_settings_and_defaults_schema() -> None:
    settings = load_settings(
        environ={
            "DB_HOST": "127.0.0.1",
            "DB_PORT": "15432",
            "DB_NAME": "tfhub",
            "DB_USER": "tfhub_ml_reader",
            "DB_PASSWORD": "secret",
        }
    )

    assert settings.db_port == 15432
    assert settings.db_schema == "public"
    assert settings.match_windows_days == (7, 14, 30, 60)


def test_match_windows_are_configurable() -> None:
    settings = load_settings(
        environ={
            "DB_HOST": "127.0.0.1",
            "DB_PORT": "15432",
            "DB_NAME": "tfhub",
            "DB_USER": "tfhub_ml_reader",
            "DB_PASSWORD": "secret",
            "MATCH_WINDOWS_DAYS": "5, 10, 21",
        }
    )

    assert settings.match_windows_days == (5, 10, 21)


@pytest.mark.parametrize("windows", ["", "0,7", "7,seven", "7,7"])
def test_rejects_invalid_match_windows(windows: str) -> None:
    values = {
        "DB_HOST": "localhost",
        "DB_PORT": "5432",
        "DB_NAME": "tfhub",
        "DB_USER": "tfhub_ml_reader",
        "DB_PASSWORD": "secret",
        "MATCH_WINDOWS_DAYS": windows,
    }
    with pytest.raises(ValueError, match="MATCH_WINDOWS_DAYS"):
        load_settings(environ=values)


def test_rejects_missing_settings() -> None:
    with pytest.raises(ValueError, match="DB_PASSWORD"):
        load_settings(environ={"DB_HOST": "localhost"})


@pytest.mark.parametrize("port", ["0", "65536", "abc"])
def test_rejects_invalid_port(port: str) -> None:
    with pytest.raises(ValueError, match="DB_PORT"):
        load_settings(
            environ={
                "DB_HOST": "localhost",
                "DB_PORT": port,
                "DB_NAME": "tfhub",
                "DB_USER": "tfhub_ml_reader",
                "DB_PASSWORD": "secret",
            }
        )


def test_rejects_schema_sql_fragments() -> None:
    values = {
        "DB_HOST": "localhost",
        "DB_PORT": "5432",
        "DB_NAME": "tfhub",
        "DB_USER": "tfhub_ml_reader",
        "DB_PASSWORD": "secret",
        "DB_SCHEMA": "public; DROP TABLE tests",
    }
    with pytest.raises(ValueError, match="DB_SCHEMA"):
        load_settings(environ=values)


def test_connection_uses_psycopg_and_read_only_default(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = Settings("localhost", 15432, "tfhub", "reader", "not-a-real-password")
    url = build_database_url(settings)
    captured = {}
    monkeypatch.setattr(
        "tfhub_ml.database.connection.create_engine",
        lambda connection_url, **kwargs: captured.update(url=connection_url, kwargs=kwargs),
    )

    create_read_only_engine(settings)

    assert url.drivername == "postgresql+psycopg"
    assert url.password == "not-a-real-password"
    assert captured["kwargs"]["connect_args"]["options"] == (
        "-c default_transaction_read_only=on"
    )
