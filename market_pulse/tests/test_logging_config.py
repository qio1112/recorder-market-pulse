import logging

from main.api.logging_config import HealthCheckAccessLogFilter


def test_health_check_access_log_filter_skips_health_path_from_uvicorn_args():
    record = logging.LogRecord(
        name="uvicorn.access",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg='%s - "%s %s HTTP/%s" %d',
        args=("127.0.0.1:12345", "GET", "/health", "1.1", 200),
        exc_info=None,
    )

    assert not HealthCheckAccessLogFilter().filter(record)


def test_health_check_access_log_filter_keeps_other_paths():
    record = logging.LogRecord(
        name="uvicorn.access",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg='%s - "%s %s HTTP/%s" %d',
        args=("127.0.0.1:12345", "GET", "/stock-symbols", "1.1", 200),
        exc_info=None,
    )

    assert HealthCheckAccessLogFilter().filter(record)
