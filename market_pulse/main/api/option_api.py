"""Option parquet data API routes."""

from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from main.data_source.option_format_conversion import (
    DEFAULT_WORKERS,
    combine_expired_parquet_files,
    convert_csv_to_parquet,
)
from main.data_source.option_parquet_reader import OptionParquetReader
from main.utils.logger_utils import setup_logging


router = APIRouter(prefix="/options", tags=["options"])
logger = setup_logging("option_api")
option_reader = OptionParquetReader()


class EmptyRequest(BaseModel):
    pass


class ExpiryDatesRequest(BaseModel):
    symbol: str = Field(..., description="Option underlying symbol.")
    today: Optional[str] = Field(
        None, description="Optional YYYY-MM-DD date override for expiry checks."
    )


class OptionHistoryRequest(BaseModel):
    symbol: str = Field(..., description="Option underlying symbol.")
    expiry: str = Field(..., description="Option expiry date in YYYY-MM-DD format.")
    option_type: str = Field(..., description="Option type: call or put.")


@router.post("/symbols")
def get_option_symbols(_: EmptyRequest):
    try:
        symbols = option_reader.get_all_tracked_symbols()
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("getting tracked option symbols failed")
        raise HTTPException(
            status_code=500, detail=f"getting tracked option symbols failed: {exc}"
        ) from exc

    return {"symbols": symbols}


@router.post("/expiry-dates")
def get_option_expiry_dates(payload: ExpiryDatesRequest):
    try:
        expiries = option_reader.get_expiry_dates(
            payload.symbol,
            today=payload.today,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("getting option expiry dates failed")
        raise HTTPException(
            status_code=500, detail=f"getting option expiry dates failed: {exc}"
        ) from exc

    return {"symbol": payload.symbol, "expiry_dates": expiries}


@router.post("/history")
def get_option_history(payload: OptionHistoryRequest):
    try:
        return option_reader.get_option_history(
            payload.symbol,
            payload.expiry,
            payload.option_type,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("getting option history failed")
        raise HTTPException(
            status_code=500, detail=f"getting option history failed: {exc}"
        ) from exc


@router.get("/convert-csv-to-parquet")
def convert_all_option_csv_to_parquet():
    try:
        converted_count = convert_csv_to_parquet(clean_target=False, workers=DEFAULT_WORKERS)
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("converting option CSV files to parquet failed")
        raise HTTPException(
            status_code=500, detail=f"converting option CSV files to parquet failed: {exc}"
        ) from exc

    return {"converted_count": converted_count, "clean_target": False, "workers": DEFAULT_WORKERS}


@router.get("/combine-expired-parquet")
def combine_expired_option_parquet():
    try:
        combined_count = combine_expired_parquet_files()
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("combining expired option parquet files failed")
        raise HTTPException(
            status_code=500, detail=f"combining expired option parquet files failed: {exc}"
        ) from exc

    return {
        "combined_count": combined_count,
    }
