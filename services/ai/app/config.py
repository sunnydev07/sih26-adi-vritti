"""Adi-Vritti AI services. Money in int paise; datetimes tz-aware."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql://adivritti:adivritti_dev@localhost:5432/adivritti"
    core_service_url: str = "http://localhost:8080"
    gap_hmac_salt: str = "dev-salt-rotate-in-prod"

    model_config = {"env_prefix": ""}


settings = Settings()
