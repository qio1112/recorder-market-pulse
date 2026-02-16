import json
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from main.data_source.data_source import (
    get_current_minute_stock_price_json,
    get_fear_greed_index_cnn,
    get_stock_price_day_history_json,
    is_today_trade_day_yf,
)
from main.tasks.update_stock_data import (
    update_stock_data,
    update_stock_data_flexible,
    get_default_option_symbols_from_file, get_default_stock_symbols_from_file
)
from main.utils.logger_utils import setup_logging

app = FastAPI(title="Market Pulse API", version="1.0.0")
logger = setup_logging("api")


def _parse_json_payload(payload: str):
    try:
        return json.loads(payload)
    except json.JSONDecodeError:
        logger.exception("Failed to parse JSON payload from upstream task")
        raise HTTPException(status_code=502, detail="Invalid upstream data format")


@app.get("/health")
def health():
    return {"status": "ok"}


class SymbolsRequest(BaseModel):
    symbols: List[str] = Field(
        ..., min_length=1, description="List of stock symbols to fetch."
    )


class UpdateStockDataRequest(BaseModel):
    update_previous_trade_date: bool = Field(
        False, description="Set true to fetch data for the nearest prior trade day."
    )
    symbols: Optional[List[str]] = Field(
        None, description="List of stock symbols to update."
    )
    option_symbols: Optional[List[str]] = Field(
        None, description="List of option symbols to update."
    )
    symbols_path: Optional[str] = Field(
        None, description="Path to file containing stock symbols (one per line)."
    )
    option_symbols_path: Optional[str] = Field(
        None, description="Path to file containing option symbols (one per line)."
    )


@app.get("/option-symbols")
def get_default_option_symbols():
    try:
        symbols = get_default_option_symbols_from_file()
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("getting option symbols from file failed")
        raise HTTPException(
            status_code=500, detail=f"getting option symbols from file failed: {exc}"
        ) from exc
    return {
        "symbols": symbols
    }


@app.get("/stock-symbols")
def get_default_option_symbols():
    try:
        symbols = get_default_stock_symbols_from_file();
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("getting stock symbols from file failed")
        raise HTTPException(
            status_code=500, detail=f"getting stock symbols from file failed: {exc}"
        ) from exc
    return {
        "symbols": symbols
    }


@app.post("/update-stock-data")
def trigger_update_stock_data(payload: UpdateStockDataRequest):
    try:
        response = update_stock_data(
            update_previous_trade_date=payload.update_previous_trade_date,
            symbols=payload.symbols,
            option_symbols=payload.option_symbols,
            symbols_path=payload.symbols_path,
            option_symbols_path=payload.option_symbols_path,
        )
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("update_stock_data failed")
        raise HTTPException(
            status_code=500, detail=f"update_stock_data failed: {exc}"
        ) from exc

    return response


@app.post("/current-minute-prices")
def current_minute_prices(payload: SymbolsRequest):
    data = _parse_json_payload(get_current_minute_stock_price_json(payload.symbols))
    return data


@app.post("/stock-daily-history")
def stock_daily_history(payload: SymbolsRequest):
    data = _parse_json_payload(get_stock_price_day_history_json(payload.symbols))
    return data


@app.get("/fear-greed-index")
def fear_greed_index(
        update_file: bool = Query(
            True, description="Append the latest value to the stored file."
        )
):
    result = get_fear_greed_index_cnn(update_file=update_file)
    if result is None:
        raise HTTPException(
            status_code=502, detail="Unable to fetch Fear & Greed Index data."
        )
    return {"value": result, "file_updated": update_file}


@app.get("/today-is-trade-day")
def today_is_trade_day():
    try:
        is_trade_day = bool(is_today_trade_day_yf())
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("Failed to evaluate trade day status")
        raise HTTPException(
            status_code=502, detail=f"Failed to evaluate trade day: {exc}"
        ) from exc

    return {"is_trade_day": is_trade_day}
