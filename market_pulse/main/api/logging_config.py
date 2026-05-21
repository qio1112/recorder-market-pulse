import logging


LOG_FORMAT = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


class HealthCheckAccessLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        path = self._extract_path(record)
        return path != "/health"

    @staticmethod
    def _extract_path(record: logging.LogRecord) -> str:
        args = record.args if isinstance(record.args, tuple) else ()
        if len(args) >= 3:
            return str(args[2]).split("?", 1)[0]

        message = record.getMessage()
        for marker in ('"GET ', '"POST ', '"PUT ', '"DELETE ', '"PATCH ', "GET ", "POST ", "PUT ", "DELETE ", "PATCH "):
            if marker in message:
                path_start = message.find(marker) + len(marker)
                path_end = message.find(" ", path_start)
                if path_end > path_start:
                    return message[path_start:path_end].split("?", 1)[0]
        return ""


def configure_uvicorn_logging() -> None:
    formatter = logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT)
    for logger_name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logger = logging.getLogger(logger_name)
        for handler in logger.handlers:
            handler.setFormatter(formatter)

    access_logger = logging.getLogger("uvicorn.access")
    if not any(isinstance(item, HealthCheckAccessLogFilter) for item in access_logger.filters):
        access_logger.addFilter(HealthCheckAccessLogFilter())
