from pathlib import Path
import os
from datetime import datetime

from main.data_source.data_source import (StockPriceData, StockOptionData, get_symbols_from_file,
                                          is_today_trade_day_yf, get_current_minute_stock_price_json, get_stock_price_day_history_json,
                                          get_fear_greed_index_cnn)
from main.utils.logger_utils import setup_logging
from main.utils.path_utils import get_resources_path


def update_stock_data(update_previous_trade_date=False,
                      symbols=None,
                      option_symbols=None,
                      symbols_path=None,
                      option_symbols_path=None):
    logger = setup_logging("update_stock_data")
    today = datetime.now().strftime('%Y-%m-%d')
    today_is_trade_day = is_today_trade_day_yf()

    now = datetime.now()
    market_open_time = now.replace(hour=9, minute=30, second=0, microsecond=0)
    market_noon_time = now.replace(hour=13, minute=0, second=0, microsecond=0)
    market_close_time = now.replace(hour=16, minute=0, second=0, microsecond=0)

    if not today_is_trade_day and not update_previous_trade_date:
        logger.info("Today is NOT a trade day, no data will be updated.")
        return
    if symbols_path is None:
        symbols_path = get_resources_path("symbols", "symbols.txt")
    if option_symbols_path is None:
        option_symbols_path = get_resources_path("symbols", "option_symbols.txt")
    if symbols is None:
        symbols = get_symbols_from_file(symbols_path)
    if option_symbols is None:
        option_symbols = get_symbols_from_file(option_symbols_path)

    logger.info(f"Found symbols: {symbols}")
    logger.info(f"Found option symbols: {option_symbols}")

    spd = StockPriceData()
    sod = StockOptionData()

    if update_previous_trade_date:
        logger.info(f"Revised date: {today}")
        today = spd.get_nearest_prior_trade_day(today, include_same=False)
        logger.info(f"Revised date: {today}")
    if now > market_close_time or update_previous_trade_date:
        logger.info("\n\n\n====================== Updating stock data... =======================\n\n")
        spd.update_stock_data_from_yf(symbols, max_workers=8)
        sod.update_option_data_from_yf(option_symbols, revised_on_date=today, max_workers=8, time_label="close")
        logger.info("\n\n====================== Stock data updated.  =======================\n\n\n")
    elif now > market_noon_time:
        logger.info("\n\n\n====================== Updating noon option data... =======================\n\n")
        sod.update_option_data_from_yf(option_symbols, revised_on_date=today, max_workers=8, time_label="noon")
        logger.info("\n\n====================== Noon option data updated.  =======================\n\n\n")
    elif now > market_open_time: # update option data only
        logger.info("\n\n\n====================== Updating morning option data... =======================\n\n")
        sod.update_option_data_from_yf(option_symbols, revised_on_date=today, max_workers=8, time_label="open")
        logger.info("\n\n====================== Morning option data updated.  =======================\n\n\n")
    else:
        logger.warning(f"Market not open (9:30 am) yet!! {now}")

    spd.close()


def update_stock_data_flexible(symbols=None, symbols_path=None, task_label="close", update_stock_data=True, update_option_data=True,
                               max_workers=8, update_today=True):
    if symbols is None and symbols_path is None:
        raise ValueError("Either symbols or symbols_path must be provided")
    logger = setup_logging("update_stock_data_flexible")
    spd = StockPriceData()
    sod = StockOptionData()
    # always get data, and always
    update_date = spd.get_nearest_prior_trade_day(datetime.now().strftime('%Y-%m-%d'), include_same=False) if update_today else datetime.now().strftime('%Y-%m-%d')
    if update_today and not is_today_trade_day_yf():
        logger.info("Today is NOT a trade date, no data will be updated.")
        return
    logger.info(f"Found symbols: {symbols}, updating for date {update_date}")
    logger.info("====================== Updating stock data =======================\n\n")
    if update_stock_data:
        spd.update_stock_data_from_yf(symbols, max_workers=8)
    if update_option_data:
        sod.update_option_data_from_yf(symbols, revised_on_date=update_date, max_workers=max_workers, time_label=task_label)
    logger.info("\n====================== Stock data updated  =======================\n\n")


def get_current_minute_stock_price_json_task(symbols: list[str]):
    logger = setup_logging("get_current_minute_stock_price_json")
    if symbols is None or len(symbols) == 0:
        logger.info("No symbols provided, exiting task.")
        return
    result = get_current_minute_stock_price_json(symbols)
    logger.info("result data:" + result)


def get_stock_price_day_history_json_task(symbols: list[str]):
    if symbols is None or len(symbols) == 0:
        return
    logger = setup_logging("get_current_minute_stock_price_json")
    result = get_stock_price_day_history_json(symbols)
    logger.info("result data:" + result)


def get_fear_greed_index_data():
    logger = setup_logging("get_fear_greed_index_data")
    data = get_fear_greed_index_cnn(update_file=True)
    logger.info(f"Fear & Greed Index result data: {data}")


def get_today_is_trade_day():
    logger = setup_logging("get_today_is_trade_day")
    today_is_trade_day = is_today_trade_day_yf()
    logger.info("result data: {}"), today_is_trade_day
