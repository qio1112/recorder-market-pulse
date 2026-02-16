import mplfinance as mpf
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np

from main.utils.path_utils import get_resources_path


def plot_stock(data: pd.DataFrame, symbol: str = "Symbol",
               highlight_dates: list = None,
               highlight_ranges: list = None,
               highlight_alpha: float = 0.4,
               highlight_color: str = "g",
               save_filename: str = None):
    if highlight_dates is None:
        highlight_dates = []
    if data.index.name.lower() != "date":
        raise ValueError("Index of data must be 'date'.")

    highlight_dicts = []
    dates_df = pd.DataFrame(data.index)

    if highlight_ranges:
        highlight_low = data["low"].min()
        highlight_high = data["high"].max()
        for h_range in highlight_ranges:
            where_dates = pd.notnull(dates_df[(dates_df >= h_range[0]) & (dates_df <= h_range[1])])["date"].values
            highlight_dicts.append(dict(y1=highlight_low, y2=highlight_high, where=where_dates,
                                        alpha=highlight_alpha, color=highlight_color))

    plot_kwargs = dict(
        type= "candle" if len(data) <= 200 else "line",
        style="charles",
        volume=True,
        # addplot=apd,
        title=f'{symbol}', ylabel='Price ($)',
        figratio=(24, 12),

        panel_ratios=(1, 0.25),
        returnfig=False
    )

    if highlight_dates:
        plot_kwargs["vlines"] = dict(vlines=highlight_dates, linewidths=2, colors='gray', alpha=0.4)

    if highlight_ranges:
        plot_kwargs["fill_between"] = highlight_dicts

    if save_filename is not None:
        plot_kwargs["savefig"] = get_resources_path("plots", save_filename)

    mpf.plot(data, **plot_kwargs)


def plot_stock_with_option(stock_data: list[pd.DataFrame], option_data: list[pd.DataFrame],
                           start_date: str, end_date: str):
    pass
    # fig, ax1 = plt.subplots()
    # # Plotting the first dataset on the left y-axis
    # color = 'tab:red'
    # ax1.set_xlabel('Date')
    # ax1.set_ylabel('Stock', color=color)
    # ax1.plot(data1.index, data1['Value'], color=color)
    # ax1.tick_params(axis='y', labelcolor=color)
    #
    # # Create a second y-axis for the second dataset
    # ax2 = ax1.twinx()
    # color = 'tab:blue'
    # ax2.set_ylabel('Second Dataset', color=color)
    # ax2.plot(data2.index, data2['Value'], color=color)
    # ax2.tick_params(axis='y', labelcolor=color)
    #
    # fig.tight_layout()  # Adjusts plot to minimize clipping of ylabel
    # plt.show()


