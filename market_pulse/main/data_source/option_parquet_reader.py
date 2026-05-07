from __future__ import annotations

import datetime
import math
from pathlib import Path
from typing import Any

import pandas as pd

from main.data_source.option_parquet_schema import OptionExpiry, OptionHistory, StrikeHistory
from main.utils.path_utils import get_resources_path


VALID_OPTION_TYPES = {"call", "put"}
DEFAULT_OPTION_PARQUET_ROOT = Path(get_resources_path("option_data_parquet"))
STRIKE_LEVEL_COLUMNS = {"contractSymbol", "contractSize"}
# Enrichment columns are intentionally left as row history so clients receive
# one value per observation date alongside bid/ask/IV and other time series.


def _strip_partition_prefix(path_name: str, prefix: str) -> str:
    if not path_name.startswith(prefix):
        raise ValueError(f"Invalid partition folder name: {path_name}")
    value = path_name[len(prefix):]
    if not value:
        raise ValueError(f"Empty partition value in folder name: {path_name}")
    return value


def _json_safe_value(value: Any) -> Any:
    if isinstance(value, pd.Timestamp):
        return value.isoformat()
    if isinstance(value, datetime.date):
        return value.isoformat()
    if hasattr(value, "item"):
        value = value.item()
    if isinstance(value, float) and math.isnan(value):
        return None
    if pd.isna(value):
        return None
    return value


class OptionParquetReader:
    def __init__(self, option_parquet_root: Path | str = DEFAULT_OPTION_PARQUET_ROOT):
        self.option_parquet_root = Path(option_parquet_root)

    def get_all_tracked_symbols(self) -> list[str]:
        if not self.option_parquet_root.exists():
            return []

        symbols = []
        for symbol_path in self.option_parquet_root.iterdir():
            if symbol_path.is_dir() and symbol_path.name.startswith("symbol="):
                symbols.append(_strip_partition_prefix(symbol_path.name, "symbol="))
        return sorted(symbols)

    def get_expiry_dates(self, symbol: str, today: datetime.date | str | None = None) -> list[OptionExpiry]:
        symbol_path = self.option_parquet_root / f"symbol={symbol}"
        if not symbol_path.exists():
            return []

        if today is None:
            today_date = datetime.date.today()
        elif isinstance(today, str):
            today_date = datetime.date.fromisoformat(today)
        else:
            today_date = today

        expiries = []
        for expiry_path in symbol_path.iterdir():
            if expiry_path.is_dir() and expiry_path.name.startswith("expiry="):
                expiry = _strip_partition_prefix(expiry_path.name, "expiry=")
                expiries.append(expiry)

        expiries = sorted(expiries)
        return [
            {
                "expiry": expiry,
                "expired": today_date > datetime.date.fromisoformat(expiry),
            }
            for expiry in expiries
        ]

    def get_option_history(self, symbol: str, expiry: str, option_type: str) -> OptionHistory:
        if option_type not in VALID_OPTION_TYPES:
            raise ValueError(f"Invalid option_type {option_type}. Expected one of {sorted(VALID_OPTION_TYPES)}")

        option_path = self.option_parquet_root / f"symbol={symbol}" / f"expiry={expiry}" / f"type={option_type}"
        parquet_files = sorted(path for path in option_path.glob("*.parquet") if path.is_file())
        if not parquet_files:
            return {
                "symbol": symbol,
                "expiry": expiry,
                "option_type": option_type,
                "strikes": [],
            }

        option_data = pd.concat((pd.read_parquet(path) for path in parquet_files), ignore_index=True)
        if option_data.empty:
            return {
                "symbol": symbol,
                "expiry": expiry,
                "option_type": option_type,
                "strikes": [],
            }

        option_data["date"] = pd.to_datetime(option_data["date"], errors="coerce")
        option_data = option_data.sort_values(by=["strike", "date", "contractSymbol"], na_position="last")
        option_data["date"] = option_data["date"].dt.strftime("%Y-%m-%d")

        strikes: list[StrikeHistory] = []
        for strike, strike_data in option_data.groupby("strike", sort=True):
            strike_data = strike_data.drop(columns=["strike", "strike_plus_date"], errors="ignore")
            strike_history: dict[str, Any] = {
                "strike": _json_safe_value(strike),
                "history": {},
            }

            for column in strike_data.columns:
                column_values = [_json_safe_value(value) for value in strike_data[column].tolist()]
                if column in STRIKE_LEVEL_COLUMNS:
                    unique_values = []
                    for value in column_values:
                        if value not in unique_values:
                            unique_values.append(value)
                    strike_history[column] = unique_values[0] if len(unique_values) == 1 else unique_values
                    continue

                strike_history["history"][column] = column_values

            strikes.append(strike_history)

        return {
            "symbol": symbol,
            "expiry": expiry,
            "option_type": option_type,
            "strikes": strikes,
        }


def get_all_tracked_symbols(option_parquet_root: Path | str = DEFAULT_OPTION_PARQUET_ROOT) -> list[str]:
    return OptionParquetReader(option_parquet_root).get_all_tracked_symbols()


def get_expiry_dates(symbol: str, option_parquet_root: Path | str = DEFAULT_OPTION_PARQUET_ROOT,
                     today: datetime.date | str | None = None) -> list[OptionExpiry]:
    return OptionParquetReader(option_parquet_root).get_expiry_dates(symbol, today)


def get_option_history(symbol: str, expiry: str, option_type: str,
                       option_parquet_root: Path | str = DEFAULT_OPTION_PARQUET_ROOT) -> OptionHistory:
    return OptionParquetReader(option_parquet_root).get_option_history(symbol, expiry, option_type)
