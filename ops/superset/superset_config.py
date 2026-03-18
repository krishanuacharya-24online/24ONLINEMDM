import os

from cachelib.redis import RedisCache

SQLALCHEMY_DATABASE_URI = os.getenv("SUPERSET_DATABASE_URI")
SECRET_KEY = os.getenv("SUPERSET_SECRET_KEY", "replace-with-a-long-random-secret")

REDIS_HOST = os.getenv("REDIS_HOST", "superset-redis")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))

RESULTS_BACKEND = RedisCache(
    host=REDIS_HOST,
    port=REDIS_PORT,
    key_prefix="superset_results",
)

FEATURE_FLAGS = {
    "ALERT_REPORTS": True,
    "EMBEDDED_SUPERSET": True,
}

GUEST_ROLE_NAME = "Gamma"
GUEST_TOKEN_JWT_SECRET = os.getenv("SUPERSET_GUEST_TOKEN_JWT_SECRET", SECRET_KEY)
GUEST_TOKEN_JWT_AUDIENCE = os.getenv("SUPERSET_GUEST_TOKEN_JWT_AUDIENCE", "superset")

ENABLE_PROXY_FIX = True
WTF_CSRF_ENABLED = os.getenv("SUPERSET_WTF_CSRF_ENABLED", "false").lower() == "true"


def _csv_env(name: str, default: str) -> list[str]:
    raw = os.getenv(name, default)
    return [item.strip() for item in raw.split(",") if item.strip()]


# Allow embedding Superset into the MDM UI origin(s) during local/dev usage.
# For production, set SUPERSET_TALISMAN_ENABLED=true and tune CSP explicitly.
SUPERSET_TALISMAN_ENABLED = os.getenv("SUPERSET_TALISMAN_ENABLED", "false").lower() == "true"
TALISMAN_ENABLED = SUPERSET_TALISMAN_ENABLED

if not TALISMAN_ENABLED:
    TALISMAN_CONFIG = {
        "force_https": False,
        "frame_options": None,
        "content_security_policy": {
            "frame-ancestors": _csv_env(
                "SUPERSET_EMBEDDING_ORIGINS",
                "http://localhost:8080,http://127.0.0.1:8080",
            ),
        },
    }


class CeleryConfig:
    broker_url = f"redis://{REDIS_HOST}:{REDIS_PORT}/0"
    result_backend = f"redis://{REDIS_HOST}:{REDIS_PORT}/1"
    imports = (
        "superset.sql_lab",
        "superset.tasks.scheduler",
        "superset.tasks.thumbnails",
        "superset.tasks.cache",
    )
    task_annotations = {
        "sql_lab.get_sql_results": {"rate_limit": "100/s"},
    }
    worker_prefetch_multiplier = 10
    task_acks_late = True
    beat_schedule = {
        "reports.scheduler": {
            "task": "reports.scheduler",
            "schedule": 60.0,
        },
        "reports.prune_log": {
            "task": "reports.prune_log",
            "schedule": 3600.0,
        },
    }


CELERY_CONFIG = CeleryConfig
