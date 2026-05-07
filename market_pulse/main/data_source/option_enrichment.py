import pandas as pd
import numpy as np
import os
import glob
import yfinance as yf
import math
from datetime import datetime, date


GREEK_COLUMNS = ["delta", "gamma", "theta", "vega", "rho"]
ENRICHMENT_COLUMNS = [
    "expiry",
    "mid",
    "stockPrice",
    "tenYearTreasuryYield",
    "dividendYield",
    "daysToExpiry",
    *GREEK_COLUMNS,
]


def _to_date(value):
    if pd.isna(value):
        return None
    if isinstance(value, pd.Timestamp):
        return value.date()
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if isinstance(value, str):
        return pd.to_datetime(value).date()
    raise TypeError(f"Unsupported type for date: {type(value)}")


# get stock daily historical data
def get_daily_history_df(symbol, start=None, ticker=None):
    ticker = ticker or yf.Ticker(symbol)
    df = ticker.history(
        period="max",  # e.g. "1d","5d","1mo","6mo","1y","max"
        start=start,
        interval="1d",  # e.g. "1m","5m","15m","1h","1d"
        prepost=False,  # False = regular market hours only
        auto_adjust=True
    )
    df.index = df.index.tz_localize(None).normalize()
    return df

def days_between(d1, d2):
    if pd.isna(d1) or pd.isna(d2):
        return np.nan

    def to_date(x):
        if isinstance(x, str):
            return datetime.strptime(x, "%Y-%m-%d").date()
        if isinstance(x, datetime):
            return x.date()
        if isinstance(x, date):
            return x
        raise TypeError(f"Unsupported type for date: {type(x)}")

    d1 = to_date(d1)
    d2 = to_date(d2)

    return (d2 - d1).days


def _normalize_history_series(history_df, value_column="Close", output_column=None, scale=1.0):
    if history_df is None or history_df.empty:
        return pd.DataFrame(columns=["date", output_column or value_column])
    if value_column not in history_df.columns:
        raise ValueError(f"History dataframe is missing {value_column} column")

    output_column = output_column or value_column
    history = history_df[[value_column]].copy()
    history.index = pd.to_datetime(history.index).tz_localize(None).normalize()
    history = history.rename(columns={value_column: output_column}).sort_index()
    history[output_column] = pd.to_numeric(history[output_column], errors="coerce") * scale
    history = history.dropna(subset=[output_column])
    history = history[~history.index.duplicated(keep="last")]
    history.index.name = "date"
    history = history.reset_index()
    return history


def _merge_history_asof(option_data, history_data, value_column):
    if history_data.empty:
        option_data[value_column] = np.nan
        return option_data

    option_data = option_data.sort_values("date")
    merged = pd.merge_asof(
        option_data,
        history_data.sort_values("date"),
        on="date",
        direction="backward",
    )
    return merged.sort_index()


def _coerce_dividend_yield(value):
    if value is None or pd.isna(value):
        return 0.0
    value = float(value)
    if value > 1:
        return value / 100
    return value


def _safe_greek(func, **kwargs):
    values = [kwargs[key] for key in ("S", "K", "iv", "r", "q")]
    if any(pd.isna(value) for value in values):
        return np.nan
    if kwargs["S"] <= 0 or kwargs["K"] <= 0 or kwargs["iv"] <= 0:
        return np.nan
    try:
        return func(**kwargs)
    except (ValueError, ZeroDivisionError, OverflowError):
        return np.nan


def _stringify_datetime_columns(df):
    df = df.copy()
    for column in df.columns:
        if pd.api.types.is_datetime64_any_dtype(df[column]):
            df[column] = df[column].astype("string").replace({"NaT": pd.NA})
    return df


def read_option_to_dfs(symbol, option_path, option_type, expiry_list=None, option_existed_longer_than=None,
                       option_expired_before_date=None):
    symbol_path = os.path.join(option_path, symbol)

    expiry_dirs = [
        d for d in os.listdir(symbol_path)
        if d.startswith(option_type + "_expire=") and os.path.isdir(os.path.join(symbol_path, d))
    ]
    print(f"Found {len(expiry_dirs)} {option_type} expiry folders for {symbol}")

    dfs = {}
    for d in sorted(expiry_dirs):
        expiry = d.split("=", 1)[1]  # get the YYYY-MM-DD part
        # skip if option expiry is later than this given date
        if option_expired_before_date is not None and days_between(expiry, option_expired_before_date) < 0:
            # print(f"Option with expiry {expiry} is after {option_expired_before_date}, so skipping it.")
            continue
        if expiry_list is None or expiry in expiry_list:
            dir_path = os.path.join(symbol_path, d)
            csv_files = glob.glob(os.path.join(dir_path, "*.csv"))
            if not csv_files:
                print(f"[WARN] No CSV files in {dir_path}")
                continue
            f = csv_files[0]
            df = pd.read_csv(f, sep="\t").dropna().drop(columns=["change", "percentChange", "contractSize"])
            df["date"] = pd.to_datetime(df["date"]).dt.normalize()
            if option_existed_longer_than is not None:
                date_diff = days_between(df["date"].min(), df["date"].max())
                if date_diff < option_existed_longer_than:
                    # print(f"Option with expiry {expiry} existed for too short time ({date_diff} days).")
                    continue
            df["expiry"] = expiry
            df["expiry"] = pd.to_datetime(df["expiry"]).dt.normalize()
            df["mid"] = round((df["bid"] + df["ask"]) / 2, 2)
            df = df[df["mid"] != 0]
            df = df[df["impliedVolatility"] != 0]
            dfs[expiry] = df.copy()
            # print(f"Read data for expiry {expiry}.")
    return dfs


def norm_cdf(x):
    """Standard normal CDF using error function (no scipy needed)."""
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2.0)))


def norm_pdf(x):
    """Standard normal PDF."""
    return (1.0 / math.sqrt(2.0 * math.pi)) * math.exp(-0.5 * x * x)


def black_scholes_delta(
        S,  # underlying price
        K,  # strike
        iv,  # implied volatility (decimal, e.g. 0.25)
        r,  # risk-free rate (decimal, e.g. 0.045)
        q,  # dividend yield (decimal, e.g. 0.01)
        expiry,  # expiry date: 'YYYY-MM-DD' or datetime/date
        today,  # current date: 'YYYY-MM-DD' or datetime/date
        option_type="call"  # "call" or "put"
):
    expiry_d = _to_date(expiry)
    today_d = _to_date(today)
    if expiry_d is None or today_d is None:
        return np.nan
    T_days = (expiry_d - today_d).days
    if T_days <= 0:
        return 0.0  # already expired (simplest convention)

    T = T_days / 365.0
    sigma = iv
    if iv == 0:
        print("iv is 0!!")
    d1 = (
                 math.log(S / K) + (r - q + 0.5 * sigma * sigma) * T
         ) / (sigma * math.sqrt(T))

    if option_type.lower() == "call":
        return round(math.exp(-q * T) * norm_cdf(d1), 3)
    elif option_type.lower() == "put":
        return round(-math.exp(-q * T) * norm_cdf(-d1), 3)
    else:
        raise ValueError("option_type must be 'call' or 'put'")


def black_scholes_gamma(
        S,  # underlying price
        K,  # strike
        iv,  # implied volatility (decimal, e.g. 0.25)
        r,  # risk-free rate (decimal)
        q,  # dividend yield (decimal)
        expiry,  # 'YYYY-MM-DD' or datetime/date
        today  # 'YYYY-MM-DD' or datetime/date
):
    expiry_d = _to_date(expiry)
    today_d = _to_date(today)
    if expiry_d is None or today_d is None:
        return np.nan
    T_days = (expiry_d - today_d).days
    if T_days <= 0:
        return 0.0  # expired

    T = T_days / 365.0
    sigma = iv

    d1 = (
                 math.log(S / K)
                 + (r - q + 0.5 * sigma * sigma) * T
         ) / (sigma * math.sqrt(T))

    gamma = math.exp(-q * T) * norm_pdf(d1) / (S * sigma * math.sqrt(T))
    return round(gamma, 3)


def black_scholes_theta(
        S,
        K,
        iv,  # implied volatility (decimal)
        r,  # risk-free rate (decimal)
        q,  # dividend yield (decimal)
        expiry,  # 'YYYY-MM-DD' or datetime/date
        today,  # 'YYYY-MM-DD' or datetime/date
        option_type="call"  # "call" or "put"
):
    expiry_d = _to_date(expiry)
    today_d = _to_date(today)
    if expiry_d is None or today_d is None:
        return np.nan

    T_days = (expiry_d - today_d).days
    if T_days <= 0:
        return 0.0

    T = T_days / 365.0
    sigma = iv

    # d1 and d2
    d1 = (math.log(S / K) + (r - q + 0.5 * sigma * sigma) * T) / (sigma * math.sqrt(T))
    d2 = d1 - sigma * math.sqrt(T)

    # common first term
    term1 = - (S * math.exp(-q * T) * norm_pdf(d1) * sigma) / (2 * math.sqrt(T))

    if option_type.lower() == "call":
        theta = (
                term1
                - r * K * math.exp(-r * T) * norm_cdf(d2)
                + q * S * math.exp(-q * T) * norm_cdf(d1)
        )
    elif option_type.lower() == "put":
        theta = (
                term1
                + r * K * math.exp(-r * T) * norm_cdf(-d2)
                - q * S * math.exp(-q * T) * norm_cdf(-d1)
        )
    else:
        raise ValueError("option_type must be 'call' or 'put'.")

    return round(theta / 365, 3)


def black_scholes_vega(
        S,  # underlying price
        K,  # strike
        iv,  # implied volatility (decimal, e.g. 0.25)
        r,  # risk-free rate (decimal)
        q,  # dividend yield (decimal)
        expiry,  # 'YYYY-MM-DD' or datetime/date
        today  # 'YYYY-MM-DD' or datetime/date
):
    expiry_d = _to_date(expiry)
    today_d = _to_date(today)
    if expiry_d is None or today_d is None:
        return np.nan

    T_days = (expiry_d - today_d).days
    if T_days <= 0:
        return 0.0

    T = T_days / 365.0
    sigma = iv

    d1 = (
                 math.log(S / K)
                 + (r - q + 0.5 * sigma * sigma) * T
         ) / (sigma * math.sqrt(T))

    vega = S * math.exp(-q * T) * norm_pdf(d1) * math.sqrt(T)
    return round(vega / 100, 3)


def black_scholes_rho(
        S,  # underlying price
        K,  # strike
        iv,  # implied volatility (decimal)
        r,  # risk-free rate (decimal)
        q,  # dividend yield (decimal)
        expiry,  # 'YYYY-MM-DD' or datetime/date
        today,  # 'YYYY-MM-DD' or datetime/date
        option_type="call"  # "call" or "put"
):
    expiry_d = _to_date(expiry)
    today_d = _to_date(today)
    if expiry_d is None or today_d is None:
        return np.nan

    T_days = (expiry_d - today_d).days
    if T_days <= 0:
        return 0.0

    T = T_days / 365.0
    sigma = iv

    d1 = (math.log(S / K) + (r - q + 0.5 * sigma * sigma) * T) / (sigma * math.sqrt(T))
    d2 = d1 - sigma * math.sqrt(T)

    if option_type.lower() == "call":
        rho = K * T * math.exp(-r * T) * norm_cdf(d2)
    elif option_type.lower() == "put":
        rho = -K * T * math.exp(-r * T) * norm_cdf(-d2)
    else:
        raise ValueError("option_type must be 'call' or 'put'.")

    return round(rho / 100, 3)


def add_delta_column(df, option_type):
    df["delta"] = df.apply(
        lambda row: _safe_greek(
            black_scholes_delta,
            S=row["stockPrice"],
            K=row["strike"],
            iv=row["impliedVolatility"],
            r=row["tenYearTreasuryYield"],
            q=row["dividendYield"],
            expiry=row["expiry"],
            today=row["date"],
            option_type=option_type
        ),
        axis=1,
    )
    return df


def add_gamma_column(df):
    df["gamma"] = df.apply(
        lambda row: _safe_greek(
            black_scholes_gamma,
            S=row["stockPrice"],
            K=row["strike"],
            iv=row["impliedVolatility"],
            r=row["tenYearTreasuryYield"],
            q=row["dividendYield"],
            expiry=row["expiry"],
            today=row["date"]
        ),
        axis=1,
    )
    return df


def add_theta_column(df, option_type):
    df["theta"] = df.apply(
        lambda row: _safe_greek(
            black_scholes_theta,
            S=row["stockPrice"],
            K=row["strike"],
            iv=row["impliedVolatility"],
            r=row["tenYearTreasuryYield"],
            q=row["dividendYield"],
            expiry=row["expiry"],
            today=row["date"],
            option_type=option_type
        ),
        axis=1,
    )
    return df


def add_vega_column(df):
    df["vega"] = df.apply(
        lambda row: _safe_greek(
            black_scholes_vega,
            S=row["stockPrice"],
            K=row["strike"],
            iv=row["impliedVolatility"],
            r=row["tenYearTreasuryYield"],
            q=row["dividendYield"],
            expiry=row["expiry"],
            today=row["date"]
        ),
        axis=1,
    )
    return df


def add_rho_column(df, option_type):
    df["rho"] = df.apply(
        lambda row: _safe_greek(
            black_scholes_rho,
            S=row["stockPrice"],
            K=row["strike"],
            iv=row["impliedVolatility"],
            r=row["tenYearTreasuryYield"],
            q=row["dividendYield"],
            expiry=row["expiry"],
            today=row["date"],
            option_type=option_type
        ),
        axis=1,
    )
    return df


def add_days_to_expiry(df):
    df["daysToExpiry"] = df.apply(
        lambda row: days_between(d1=row["date"], d2=row["expiry"]),
        axis=1
    )
    df = df[df["daysToExpiry"] > 0]  # sometimes there is issue with the data on expiry date, so drop it
    df = df.copy()
    return df


def get_dividend_rate_yf(symbol):
    ticker_info = yf.Ticker(symbol).info
    return _coerce_dividend_yield(ticker_info.get("dividendYield"))


def get_symbol_enrichment_data_yf(symbol, start=None, ticker=None):
    ticker = ticker or yf.Ticker(symbol)
    stock_history_df = get_daily_history_df(symbol, start=start, ticker=ticker)
    ticker_info = ticker.info
    dividend_yield = _coerce_dividend_yield(ticker_info.get("dividendYield"))
    return stock_history_df, dividend_yield


def get_risk_free_df_yf(risk_free_symbol="^TNX"):
    return get_daily_history_df(risk_free_symbol)


def enrich_option_rows(
        df,
        symbol,
        option_type,
        expiry,
        stock_history_df=None,
        risk_free_df=None,
        dividend_yield=None,
):
    option_data = df.copy()
    option_data["date"] = (
        pd.to_datetime(option_data["date"], errors="coerce")
        .dt.tz_localize(None)
        .dt.normalize()
    )
    option_data["expiry"] = pd.to_datetime(expiry, errors="coerce").normalize()

    for column in ["strike", "bid", "ask", "impliedVolatility"]:
        if column in option_data.columns:
            option_data[column] = pd.to_numeric(option_data[column], errors="coerce")

    if "mid" not in option_data.columns and {"bid", "ask"}.issubset(option_data.columns):
        option_data["mid"] = ((option_data["bid"] + option_data["ask"]) / 2).round(2)

    if stock_history_df is None and dividend_yield is None:
        start = option_data["date"].min()
        stock_history_df, dividend_yield = get_symbol_enrichment_data_yf(symbol, start=start)
    elif stock_history_df is None:
        start = option_data["date"].min()
        stock_history_df = get_daily_history_df(symbol, start=start)
    if risk_free_df is None:
        risk_free_df = get_risk_free_df_yf()
    if dividend_yield is None:
        dividend_yield = get_dividend_rate_yf(symbol)

    stock_history = _normalize_history_series(stock_history_df, output_column="stockPrice")
    risk_free_history = _normalize_history_series(
        risk_free_df,
        output_column="tenYearTreasuryYield",
        scale=0.01,
    )

    option_data = _merge_history_asof(option_data, stock_history, "stockPrice")
    option_data = _merge_history_asof(option_data, risk_free_history, "tenYearTreasuryYield")
    option_data["dividendYield"] = _coerce_dividend_yield(dividend_yield)

    option_data = add_days_to_expiry(option_data)
    option_data = add_delta_column(option_data, option_type)
    option_data = add_gamma_column(option_data)
    option_data = add_theta_column(option_data, option_type)
    option_data = add_vega_column(option_data)
    option_data = add_rho_column(option_data, option_type)

    option_data["date"] = option_data["date"].dt.strftime("%Y-%m-%d")
    option_data["expiry"] = option_data["expiry"].dt.strftime("%Y-%m-%d")
    option_data = _stringify_datetime_columns(option_data)
    return option_data
