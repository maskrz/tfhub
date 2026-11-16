"""PostgreSQL connection setup with read-only transaction defaults."""

from sqlalchemy import URL, create_engine
from sqlalchemy.engine import Engine

from tfhub_ml.config.settings import Settings


def build_database_url(settings: Settings) -> URL:
    return URL.create(
        "postgresql+psycopg",
        username=settings.db_user,
        password=settings.db_password,
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
    )


def create_read_only_engine(settings: Settings) -> Engine:
    """Create an engine that requests read-only behavior from PostgreSQL."""
    return create_engine(
        build_database_url(settings),
        connect_args={"options": "-c default_transaction_read_only=on"},
        pool_pre_ping=True,
    )
