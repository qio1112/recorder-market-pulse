from main.data_source.data_source import get_fear_greed_index_cnn
from main.tasks.update_stock_data import update_stock_data, update_stock_data_flexible, \
    get_current_minute_stock_price_json_task, get_stock_price_day_history_json_task, get_fear_greed_index_data

import argparse

if __name__ == "__main__":
    # python -m main.main
    parser = argparse.ArgumentParser(description="Stock and Option Data")

    parser.add_argument(
        "--jobName",
        type=str,
        default="update_stock_data",
        help="'update_stock_data' for the existing job, 'update_stock_data_flexible' for new flexible job, 'get_current_minute_stock_price_json' for minute level recent stock prices in json, 'get_stock_price_day_history_json' for historical daily stock prices",
    )

    parser.add_argument(
        "--symbolPath",
        type=str,
        default=None,
        help="path of file with symbols in each row",
    )

    parser.add_argument(
        "--optionSymbolPath",
        type=str,
        default=None,
        help="path of file with Option symbols in each row",
    )

    parser.add_argument(
        "--symbols",
        type=lambda s: [p.strip() for p in s.split(",")],
        default=None,
        help="list of stock symbols, comma separated",
    )

    parser.add_argument(
        "--optionSymbols",
        type=lambda s: [p.strip() for p in s.split(",")],
        default=None,
        help="list of option symbols, comma separated",
    )

    parser.add_argument(
        "--taskLabel",
        type=str,
        default="close",
        help="task label, default is 'close'",
    )

    parser.add_argument(
        "--maxWorkers",
        type=int,
        default=8,
        help="number of parallel workers, default is 8",
    )

    parser.add_argument(
        "--update_most_recent_date",
        type=bool,
        default=False,
        help="set if update the previous trade date dynamically, otherwise will update for current date if it's market date",
    )

    args = parser.parse_args()

    job_name = args.jobName
    symbol_path = args.symbolPath
    option_symbol_path = args.optionSymbolPath
    symbols = args.symbols
    option_symbols = args.optionSymbols
    update_most_recent_date = args.update_most_recent_date
    task_label = args.taskLabel
    max_workers = args.maxWorkers

    if job_name == "update_stock_data":
        update_stock_data(update_previous_trade_date=update_most_recent_date,
                          symbols=symbols,
                          option_symbols=option_symbols,
                          symbols_path=symbol_path,
                          option_symbols_path=option_symbol_path
                          )
    elif job_name == "update_stock_data_flexible":
        update_stock_data_flexible(symbols=symbols,
                                   symbols_path=symbol_path,
                                   task_label=task_label,
                                   update_stock_data=True,
                                   update_option_data=True,
                                   max_workers=max_workers,
                                   update_today=not update_most_recent_date)
    elif job_name == "get_current_minute_stock_price_json":
        get_current_minute_stock_price_json_task(symbols=symbols)
    elif job_name == "get_stock_price_day_history_json":
        get_stock_price_day_history_json_task(symbols=symbols)
    elif job_name == "get_fear_greed_index_data":
        get_fear_greed_index_data()
    else:
        raise ValueError(f"Unknown job name: {job_name}")
