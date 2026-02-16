import os
from pathlib import Path
import shutil
import pandas as pd


def get_resources_path(*subpaths):
    return os.path.join(Path(os.path.abspath(__file__)).parent.parent.parent, "resources", *subpaths)


# this is a one time function to combine option data to single csv file for each expiration date
def combine_existing_option_files(option_path_name):
    base_path = get_resources_path(option_path_name)
    symbol_paths = os.listdir(base_path)
    for symbol in symbol_paths:
        symbol_path = os.path.join(base_path, symbol)
        if os.path.isdir(symbol_path):
            call_put_expire_paths = os.listdir(symbol_path)
            for call_put_expire in call_put_expire_paths:
                call_put_expire_path = os.path.join(symbol_path, call_put_expire)
                if os.path.isdir(call_put_expire_path):
                    expire_date = call_put_expire.split("=")[1]
                    option_type = call_put_expire.split("_")[0]
                    df = None
                    on_date_paths = os.listdir(call_put_expire_path)
                    for on_date in on_date_paths:
                        on_date_path = os.path.join(call_put_expire_path, on_date)
                        if os.path.isdir(on_date_path):
                            csv_file_paths = os.listdir(on_date_path)
                            for csv_file in csv_file_paths:
                                csv_file_path = os.path.join(on_date_path, csv_file)
                                if os.path.isfile(csv_file_path) and csv_file.endswith(".csv"):
                                    cur_df = pd.read_csv(csv_file_path, sep="\t")
                                    if len(cur_df) > 0:
                                        # cur_df = cur_df.dropna(axis=1, how='all')
                                        cur_df["date"] = pd.to_datetime(on_date)
                                        cur_df["strike_plus_date"] = cur_df["strike"].astype("str") + "_" + on_date
                                        cur_df.drop(["currency", "Unnamed: 0"], axis=1, inplace=True)
                                        cur_df.set_index("strike_plus_date", inplace=True)
                                        if df is None:
                                            df = cur_df
                                        else:
                                            df = pd.concat([df, cur_df], ignore_index=False)
                        if os.path.isdir(on_date_path):
                            shutil.rmtree(on_date_path)
                        else:
                            os.remove(on_date_path)
                    filename = f"{symbol}_{option_type}_expire={expire_date}.csv"
                    if df is not None:
                        print(f"writing {symbol} {option_type} options with exp_date={expire_date} to combined csv...")
                        column_names = ["date", "strike", "contractSymbol", "lastTradeDate",
                                        "lastPrice",
                                        "bid", "ask", "change", "percentChange", "volume", "openInterest",
                                        "impliedVolatility", "inTheMoney", "contractSize"]
                        df = df[column_names]
                        df.to_csv(os.path.join(call_put_expire_path, filename), sep="\t")
                        print(f"writing {symbol} {option_type} options with exp_date={expire_date} done!!")
                    else:
                        print(f"No data for {symbol} {option_type} options with exp_date={expire_date}!!")
