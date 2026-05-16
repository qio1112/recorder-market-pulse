import sqlite3 as db
import time

import yfinance as yf
import pandas as pd
import os
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
import datetime
import json
import requests

from main.data_source.option_format_conversion import DEFAULT_WORKERS
from main.data_source.option_enrichment import (
    enrich_option_rows,
    get_symbol_enrichment_data_yf,
    get_risk_free_df_yf,
)
from main.utils.logger_utils import setup_logging
from main.utils.path_utils import get_resources_path

logger = setup_logging(__name__)


class StockPriceData:

    def __init__(self, db_name: str = "stock_data.db"):
        logger.info("Initializing database connection for stock_data...")
        self.db_path = get_resources_path(db_name)
        logger.info(f"db_path: {self.db_path}")
        self.db_conn = db.connect(self.db_path)
        # create database if not exist
        self.init_db()
        logger.info("DataSource Ready")
        self.df_dict = dict()

    def init_db(self):
        cursor = self.db_conn.cursor()
        try:
            cursor.execute('''CREATE TABLE IF NOT EXISTS stock_prices
                        (symbol TEXT not null,
                        date DATE not null,
                        open REAL not null,
                        close REAL not null,
                        high REAL not null,
                        low REAL not null,
                        volume INTEGER not null,
                        dividends REAL,
                        splits REAL,
                        PRIMARY KEY (symbol, date))''')
            self.db_conn.commit()
        finally:
            cursor.close()

    def close(self):
        self.db_conn.commit()
        self.db_conn.close()

    def check_local_availability(self, symbol: str, date: str = None, cursor: db.Cursor = None):
        if cursor is None:
            cursor = self.db_conn.cursor()
        try:
            if date is None:
                data = cursor.execute("SELECT 1 FROM stock_prices WHERE symbol = ?", (symbol,))
            else:
                data = cursor.execute("SELECT 1 FROM stock_prices WHERE symbol = ? AND date = ?", (symbol, date))
            return data.fetchone() is not None
        finally:
            cursor.close()

    def get_last_available_date_local(self, symbol: str, cursor: db.Cursor = None):
        if cursor is None:
            cursor = self.db_conn.cursor()
        try:
            last_available_date = cursor.execute("SELECT MAX(date) FROM stock_prices WHERE symbol = ?",
                                                 (symbol,)).fetchone()
            return last_available_date[0]
        finally:
            cursor.close()

    def get_first_available_date_local(self, symbol: str, cursor: db.Cursor = None):
        if cursor is None:
            cursor = self.db_conn.cursor()
        try:
            last_available_date = cursor.execute("SELECT MIN(date) FROM stock_prices WHERE symbol = ?",
                                                 (symbol,)).fetchone()
            return last_available_date[0]
        finally:
            cursor.close()

    def update_stock_data_from_yf(self, symbols: str | list[str], update_existing: bool = False, max_workers: int = 8):
        if isinstance(symbols, str):
            symbols = [symbols]
        if update_existing and len(symbols) > 1:
            raise ValueError("Can only update stock existing data for 1 symbol!")
        cursor = None
        try:
            dataframes = []
            symbol_with_data = []
            num_workers = min(len(symbols), max_workers)
            with ThreadPoolExecutor(max_workers=num_workers) as executor:
                futures = {executor.submit(self.get_stock_prices_yf, symbol): symbol for symbol in symbols}
                for future in futures:
                    result = future.result()
                    if not result[0].empty:
                        dataframes.append(result[0])
                        symbol_with_data.append(result[1])

            combined_data = pd.concat(dataframes)
            if combined_data.empty:
                logger.error(f"No data found for list of symbols: {symbols}.")
                raise ValueError(f"No data found for list of symbols: {symbols}. None of the symbols were valid.")
            else:
                logger.info(f"Fetched data for {symbol_with_data}, updating database")
                # save new data to a temp table
                cursor = self.db_conn.cursor()
                cursor.execute("DROP TABLE IF EXISTS temp_stock_prices")
                cursor.execute('''
                        CREATE TABLE temp_stock_prices
                        (symbol TEXT not null,
                        date DATE not null,
                        open REAL not null,
                        close REAL not null,
                        high REAL not null,
                        low REAL not null,
                        volume INTEGER not null,
                        dividends REAL,
                        splits REAL,
                        PRIMARY KEY (symbol, date))''')
                combined_data.to_sql('temp_stock_prices', self.db_conn, if_exists='replace', index=False)
                # self.db_conn.commit()

                if update_existing:
                    # update existing records in db with temp table
                    cursor.execute('''
                        UPDATE stock_prices
                        SET
                            open = (SELECT open FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            high = (SELECT high FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            low = (SELECT low FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            close = (SELECT close FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            volume = (SELECT volume FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            dividends = (SELECT dividends FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date),
                            splits = (SELECT splits FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date)
                        WHERE EXISTS (
                            SELECT 1 FROM temp_stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date
                        );''')

                cursor.execute('''
                    INSERT INTO stock_prices (symbol, date, open, high, low, close, volume, dividends, splits)
                    SELECT symbol, date, open, high, low, close, volume, dividends, splits
                    FROM temp_stock_prices
                    WHERE NOT EXISTS (
                        SELECT 1 FROM stock_prices WHERE stock_prices.symbol = temp_stock_prices.symbol AND stock_prices.date = temp_stock_prices.date
                    );''')
                self.db_conn.commit()
                cursor.execute("DROP TABLE temp_stock_prices")
                logger.info(f"Database updated for {symbol_with_data}.")

                # update df buffer
                for symbol in symbol_with_data:
                    if symbol in self.df_dict:
                        self.get_stock_data(symbol, update_dict=True)

        except Exception as e:
            raise e
        finally:
            self.db_conn.commit()
            if cursor:
                cursor.close()

    def get_stock_data_on_dates(self, symbol: str, dates: str | list[str]):
        df = self.get_stock_data(symbol)
        if isinstance(dates, str):
            dates = [dates]
        if isinstance(df, pd.DataFrame) and not df.empty:
            trade_dates = pd.to_datetime([self.get_nearest_next_trade_day(d) for d in dates])
            return df[df.index.isin(trade_dates)]

    def get_stock_data(self, symbol: str, start_date: str = None, end_date: str = None,
                       as_df: bool = True, update_dict: bool = False):
        if as_df:
            query = f"SELECT * FROM stock_prices WHERE symbol = \"{symbol}\""
            df = self.df_dict.get(symbol, None)
            if not isinstance(df, pd.DataFrame) or update_dict:
                df = pd.read_sql(query, self.db_conn)
                df['date'] = pd.to_datetime(df['date'])
                df.set_index("date", inplace=True)
                self.df_dict[symbol] = df
            condition = (df["symbol"] == symbol)
            if start_date:
                condition &= (df.index >= pd.to_datetime(start_date))
            if end_date:
                condition &= (df.index <= pd.to_datetime(end_date))
            selected_data = df[condition]
            return selected_data.copy()
        else:
            cursor = self.db_conn.cursor()
            query = "SELECT * FROM stock_prices WHERE symbol = ?"
            params = [symbol]

            if start_date and end_date:
                query += " AND date BETWEEN ? AND ?"
                params.extend([start_date, end_date])
            elif start_date:
                query += " AND date >= ?"
                params.append(start_date)
            elif end_date:
                query += " AND date <= ?"
                params.append(end_date)
            return cursor.execute(query, params).fetchall()

    def delete_stock_data(self, symbol: str, cursor: db.Cursor = None):
        if not self.check_local_availability(symbol):
            logger.warning(f"No stock data available for {symbol}.")
        else:
            if cursor is None:
                cursor = self.db_conn.cursor()
            try:
                cursor.execute("DELETE FROM stock_prices WHERE symbol = ?", (symbol,))
                self.db_conn.commit()
                logger.info(f"Deleted stock data for symbol {symbol}.")
            finally:
                cursor.close()

    def get_local_symbols(self, cursor: db.Cursor = None):
        if cursor is None:
            cursor = self.db_conn.cursor()
        try:
            symbols = cursor.execute("SELECT DISTINCT symbol FROM stock_prices").fetchall()
            if symbols:
                return [s[0] for s in symbols]
            else:
                return None
        finally:
            cursor.close()

    def get_stock_prices_yf(self, symbol: str):
        ticker = yf.Ticker(symbol)
        history = ticker.history(period="max", prepost=False)
        if history.empty:
            logger.warning(f"{symbol} not available from YFinance!")
        else:
            history.reset_index(inplace=True)
            history["Date"] = history["Date"].dt.date
            history["Symbol"] = symbol
            history.rename(
                columns={"Symbol": "symbol", "Date": "date", "Open": "open", "High": "high", "Low": "low",
                         "Close": "close", "Volume": "volume", "Dividends": "dividends", "Stock Splits": "splits"},
                inplace=True)
            logger.info(f"Stock data for {symbol} fetched from YF.")
        return history, symbol

    def get_historical_trade_days(self, based_on: str = "^SPX", cursor: db.Cursor = None):
        if not self.check_local_availability(based_on):
            self.update_stock_data_from_yf(based_on)
            logger.warning(f"No local data found for {based_on}, updated data from YF.")
        if not cursor:
            cursor = self.db_conn.cursor()
        try:
            dates = cursor.execute("SELECT date FROM stock_prices WHERE symbol = ?", (based_on,)).fetchall()
            return [date[0] for date in dates]
        finally:
            cursor.close()

    def get_nearest_next_trade_day(self, date: str, include_same: True, based_on: str = "^SPX",
                                   cursor: db.Cursor = None):
        df = self.get_stock_data(based_on, cursor)
        if pd.to_datetime(date) not in df.index or not include_same:
            return df.index[df.index > date][0].strftime('%Y-%m-%d')
        else:
            return date

    def get_nearest_prior_trade_day(self, date: str, include_same: True, based_on: str = "^SPX",
                                    cursor: db.Cursor = None):
        df = self.get_stock_data(based_on, cursor)
        if pd.to_datetime(date) not in df.index or not include_same:
            return df.index[df.index < date][-1].strftime('%Y-%m-%d')
        else:
            return date


class StockOptionData:
    def __init__(self, source_folder_name: str = "option_data"):
        self.source_path = get_resources_path(source_folder_name)
        self.parquet_source_path = get_resources_path(f"{source_folder_name}_parquet")
        logger.info(f"Option Source Path: {self.source_path}")
        logger.info(f"Option Parquet Source Path: {self.parquet_source_path}")
        self.df_dict = dict()

    def update_option_data_from_yf(self, symbols: str | list[str], revised_on_date: str = None,
                                   max_workers: int = DEFAULT_WORKERS,
                                   time_label: str = "close", to_parquet_file: bool = True):
        if isinstance(symbols, str):
            symbols = [symbols]
        requested_workers = DEFAULT_WORKERS if max_workers is None else max_workers
        num_workers = min(len(symbols), requested_workers, DEFAULT_WORKERS)
        logger.info(
            f"Updating option data for symbols: {symbols} (with {num_workers} workers). time_label = {time_label}")
        risk_free_df = get_risk_free_df_yf() if to_parquet_file else None
        remaining_symbols = symbols
        retry_count = 0
        while remaining_symbols is not None and len(remaining_symbols) > 0:
            if retry_count > 7:
                logger.error(
                    f"Failed to update option data for symbols {remaining_symbols} for date {revised_on_date} after 7 retries!")
                break
            if retry_count > 0:
                wait_time = (retry_count + 2) ** 2
                logger.info(f"Retry getting option data after {wait_time} seconds....")
                time.sleep(wait_time)
            with ProcessPoolExecutor(max_workers=num_workers) as executor:
                failed_symbols_itr = executor.map(self.write_option_to_file, remaining_symbols,
                                                  [revised_on_date] * len(remaining_symbols),
                                                  [False] * len(remaining_symbols),
                                                  [False] * len(remaining_symbols),
                                                  [time_label] * len(remaining_symbols),
                                                  [to_parquet_file] * len(remaining_symbols),
                                                  [risk_free_df] * len(remaining_symbols))
                failed_symbols = []
                for failed_symbol in failed_symbols_itr:
                    failed_symbols.append(failed_symbol)
                finished_symbols = [sym for sym in remaining_symbols if sym not in failed_symbols]
                remaining_symbols = [sym for sym in failed_symbols if sym is not None]
                logger.info(f"Finished updating option data for symbols: {finished_symbols}")
                if len(remaining_symbols) > 0:
                    logger.info(f"Remaining symbols (with failures): {remaining_symbols}, will retry")
                    retry_count += 1
        if len(remaining_symbols) > 0:
            logger.error(f"Failed to update option data for symbols: {remaining_symbols}")
        else:
            logger.info("Finished updating option data for all symbols")

    def get_option_chain_path(self, symbol: str, option_type: str, expire_date: str, on_date: str = None,
                              mkdir: bool = False, time_label: str = "close"):
        if option_type != "call" and option_type != "put":
            logger.error(f"Invalid option type {option_type}")
            raise ValueError(f"Invalid option type {option_type}")
        if time_label == "close":
            file_path = self.source_path
        else:
            # updating option for some intra day time
            file_path = self.source_path + "_" + time_label

        file_path = os.path.join(file_path, symbol, f"{option_type}_expire={expire_date}")
        if on_date is not None:
            file_path = os.path.join(file_path, on_date)
            file_name = f"{symbol}_{option_type}_expire={expire_date}_on={on_date}.csv"
        else:
            # use this file name for a combined csv file
            file_name = f"{symbol}_{option_type}_expire={expire_date}.csv"
        if mkdir:
            os.makedirs(file_path, exist_ok=True)
        return file_path, file_name

    def get_option_chain_parquet_path(self, symbol: str, option_type: str, expire_date: str, revised_date: str,
                                      mkdir: bool = False, time_label: str = "close"):
        if option_type != "call" and option_type != "put":
            logger.error(f"Invalid option type {option_type}")
            raise ValueError(f"Invalid option type {option_type}")
        if time_label == "close":
            file_path = self.parquet_source_path
        else:
            file_path = self.parquet_source_path + "_" + time_label

        file_path = os.path.join(file_path, f"symbol={symbol}", f"expiry={expire_date}", f"type={option_type}")
        file_name = f"{symbol}_{expire_date}_{option_type}_{revised_date}.parquet"
        if mkdir:
            os.makedirs(file_path, exist_ok=True)
        return file_path, file_name

    def write_option_to_file(self, symbol: str, revised_date: str = None,
                             to_split_csv: bool = False, to_combined_csv: bool = True, time_label: str = "close",
                             to_parquet_file: bool = True, risk_free_df: pd.DataFrame = None):
        today = str(datetime.date.today())
        if revised_date:
            today = revised_date

        if not to_split_csv and not to_combined_csv and not to_parquet_file:
            logger.warning(f"All option output settings are False. Not writing option data to anywhere!! ")
        try:
            ticker = yf.Ticker(symbol)
            exp_dates = ticker.options
            stock_history_df = None
            dividend_yield = None
            if to_parquet_file:
                logger.info(f"Loading enrichment data for option parquet update: {symbol}")
                stock_history_df, dividend_yield = get_symbol_enrichment_data_yf(symbol, ticker=ticker)
                if risk_free_df is None:
                    risk_free_df = get_risk_free_df_yf()
            for exp_date in exp_dates:
                option_chain = ticker.option_chain(exp_date)
                if to_split_csv:
                    call_file_dir, call_file_name = self.get_option_chain_path(symbol, "call", exp_date,
                                                                               today, mkdir=True, time_label=time_label)
                    put_file_dir, put_file_name = self.get_option_chain_path(symbol, "put", exp_date, today,
                                                                             mkdir=True, time_label=time_label)

                    option_chain[0].to_csv(os.path.join(call_file_dir, call_file_name), sep='\t', encoding='utf-8')
                    option_chain[1].to_csv(os.path.join(put_file_dir, put_file_name), sep='\t', encoding='utf-8')
                column_names = ["date", "strike", "contractSymbol", "lastTradeDate",
                                "lastPrice",
                                "bid", "ask", "change", "percentChange", "volume", "openInterest",
                                "impliedVolatility", "inTheMoney", "contractSize"]
                option_dataframes = {
                    "call": option_chain[0],
                    "put": option_chain[1],
                }
                for option_type, option_df in option_dataframes.items():
                    if option_df is None or len(option_df) == 0:
                        logger.warning(f"No {option_type.upper()} data for {symbol} for exp_date {exp_date}")
                        continue

                    option_df = option_df.copy()
                    option_df["date"] = pd.to_datetime(today)
                    option_df["strike_plus_date"] = option_df["strike"].astype("str") + "_" + today
                    option_df.drop(["currency"], axis=1, inplace=True)
                    option_df.set_index("strike_plus_date", inplace=True)
                    option_df = option_df[column_names]

                    if to_combined_csv:
                        latest_updated_date = self.get_latest_updated_date(symbol, option_type, exp_date,
                                                                           time_label=time_label)
                        if latest_updated_date is None or today > latest_updated_date:
                            file_dir, file_name = self.get_option_chain_path(symbol, option_type, exp_date,
                                                                             mkdir=True, time_label=time_label)
                            file_path = os.path.join(file_dir, file_name)
                            file_exists = os.path.exists(file_path)
                            option_df.to_csv(file_path, mode="a", sep="\t", header=not file_exists)
                        # else:
                        # logger.warning(
                        #     f"{option_type.upper()} options time_label={time_label} for {symbol} exp_date={exp_date} is already updated on {latest_updated_date}!")

                    if to_parquet_file:
                        file_dir, file_name = self.get_option_chain_parquet_path(symbol, option_type, exp_date, today,
                                                                                 mkdir=True, time_label=time_label)
                        parquet_path = os.path.join(file_dir, file_name)
                        if os.path.exists(parquet_path):
                            continue
                        parquet_df = option_df.reset_index()
                        parquet_df = enrich_option_rows(
                            parquet_df,
                            symbol=symbol,
                            option_type=option_type,
                            expiry=exp_date,
                            stock_history_df=stock_history_df,
                            risk_free_df=risk_free_df,
                            dividend_yield=dividend_yield,
                        )
                        parquet_df.to_parquet(parquet_path, index=False)
            logger.info(f"Option of {symbol} is updated.")
            return None
        except Exception as e:
            logger.error(f"Failed to update option of {symbol}.")
            logger.error(e)
            return symbol

    def get_local_symbols(self):
        # return [file for file in os.listdir(self.source_path) if os.path.isfile(os.path.join(self.source_path, file))]
        return [file for file in os.listdir(self.source_path)]

    def get_expiration_dates(self, symbol: str, include_expired: bool = False):
        symbol_option_path = os.path.join(self.source_path, symbol)
        expiration_dates = set()
        today = str(datetime.date.today())
        if os.path.exists(symbol_option_path):
            expiration_date_folders = os.listdir(symbol_option_path)
            for folder_name in expiration_date_folders:
                if os.path.isdir(os.path.join(symbol_option_path, folder_name)):
                    expiration_date = folder_name.split("=")[1]
                    if include_expired or today <= expiration_date:
                        expiration_dates.add(expiration_date)
        return expiration_dates

    def get_strike_prices(self, symbol: str, option_type: str, expiration_date: str):
        data = self.get_option_data(symbol, option_type, expiration_date)
        if data is not None and len(data) > 0:
            return data["strike"].unique().tolist()
        return None

    def get_option_data(self, symbol: str, option_type: str, expiration_date: str, strike: float = None,
                        update_data: bool = False, time_label: str = "close"):
        df_dict_key = symbol + "_" + option_type + "_" + expiration_date
        if df_dict_key in self.df_dict and not update_data:
            df = self.df_dict[df_dict_key]
        else:
            file_path, file_name = self.get_option_chain_path(symbol, option_type, expiration_date,
                                                              mkdir=False, time_label=time_label)
            if not os.path.exists(os.path.join(file_path, file_name)):
                logger.warning(f"No option file found under path {file_path}/{file_name}")
                return None
            data_types = {
                "strike_plus_date": "str",
                "contractSymbol": "str",
                "contractSize": "str",
                "strike": "float",
                "lastPrice": "float",
                "bid": "float",
                "ask": "float",
                "change": "float",
                "volume": "float",
                "openInterest": "float",
                "impliedVolatility": "float",
                "inTheMoney": "bool",
                "date": "str",
                "lastTradeDate": "str"
            }
            df = pd.read_csv(os.path.join(file_path, file_name),
                             sep="\t",
                             index_col="strike_plus_date",
                             dtype=data_types)
            df["volume"] = df["volume"].fillna(0)
            df['date'] = pd.to_datetime(df['date'], errors='coerce')  # Adjust format if necessary
            self.df_dict[df_dict_key] = df

        if strike is not None:
            df = df[df["strike"] == strike].sort_values(by="date")

        return df.copy()

    def get_latest_updated_date(self, symbol: str, option_type: str, expiration_date: str, time_label: str = "close"):
        data = self.get_option_data(symbol, option_type, expiration_date, time_label=time_label)
        if data is None or len(data) == 0:
            return None
        return data["date"].max().strftime("%Y-%m-%d")


def get_symbols_from_file(symbols_file_path: str):
    with open(symbols_file_path, 'r') as input_file:
        symbols = []
        for line in input_file:
            symbols.append(line.strip())
        return symbols




def is_today_trade_day_yf():
    # Fetch data for the specified ticker
    data = yf.Ticker("^SPX").history(period="1d")
    # Get today's date in the same format as the data's index
    today = datetime.datetime.now().strftime('%Y-%m-%d')
    # Check if the latest data's date matches today's date
    if data.index[-1].strftime('%Y-%m-%d') == today:
        return True
    else:
        return False


def get_current_minute_stock_price_json(symbols: list[str]):
    df = yf.download(tickers=symbols,
                     period="1d",  # e.g. "1d","5d","1mo","6mo","1y","max"
                     interval="1m",  # e.g. "1m","5m","15m","1h","1d"
                     group_by="ticker",  # keeps tickers separated in columns
                     prepost=False,  # False = regular market hours only
                     auto_adjust=True
                     )
    latest_rows = []
    symbols_not_found = []
    for sym in symbols:
        sub = df[sym].dropna()
        if sub.empty:
            symbols_not_found.append(sym)
        else:
            ts = str(sub.index[-1])
            row = sub.loc[ts, ["Open", "High", "Low", "Close", "Volume"]]
            latest_rows.append(
                {"Symbol": sym, "Datetime": ts, **row.to_dict()}
            )

    result = {
        "InvalidSymbols": symbols_not_found,
        "Data": latest_rows
    }

    return json.dumps(result)


def get_stock_price_day_history_json(symbols: list[str]):
    df = yf.download(tickers=symbols,
                     period="max",  # e.g. "1d","5d","1mo","6mo","1y","max"
                     interval="1d",  # e.g. "1m","5m","15m","1h","1d"
                     group_by="ticker",  # keeps tickers separated in columns
                     prepost=False,  # False = regular market hours only
                     auto_adjust=True
                     )
    symbols_not_found = []
    stock_price_by_symbol = []
    for sym in symbols:
        sub = df[sym].dropna().reset_index()
        if sub.empty:
            symbols_not_found.append(sym)
        else:
            sub["Date"] = sub["Date"].dt.strftime("%Y-%m-%d")
            sub = sub.rename(columns={"Date": "Datetime"})
            stock_price_by_symbol.append({
                "Symbol": sym,
                **sub.to_dict(orient="list")
            })
    result = {
        "InvalidSymbols": symbols_not_found,
        "Data": stock_price_by_symbol
    }
    return json.dumps(result)


def get_fear_greed_index_cnn(update_file: bool = False):
    URL = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata"

    HEADERS = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/117.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://www.cnn.com/markets/fear-and-greed",
        "Origin": "https://www.cnn.com",
        "Connection": "keep-alive",
    }
    try:
        resp = requests.get(URL, headers=HEADERS)
        resp.raise_for_status()
        data = resp.json()

        points = data["fear_and_greed_historical"]["data"]
        latest = points[-1]
        timestamp = datetime.datetime.fromtimestamp(latest["x"] / 1000)
        value = latest["y"]
    except Exception as e:
        logger.error(f"Error fetching Fear & Greed Index data: {e}")
        return None
    result = f"{timestamp}, {value:.2f}"

    if update_file:
        file_path = get_resources_path("fear_greed_index_cnn.txt")
        with open(file_path, "a") as f:
            f.write(result + "\n")
        logger.info(f"Fear & Greed Index (CNN) updated to {file_path}")

    return result
