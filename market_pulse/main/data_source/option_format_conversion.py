from __future__ import annotations

import argparse
import logging
import os
import shutil
from concurrent.futures import ProcessPoolExecutor, as_completed
from datetime import date
from pathlib import Path

import pandas as pd

try:
    from main.data_source.option_enrichment import (
        enrich_option_rows,
        get_symbol_enrichment_data_yf,
        get_risk_free_df_yf,
    )
except ModuleNotFoundError:  # Allows running this file directly from data_source/.
    from option_enrichment import (
        enrich_option_rows,
        get_symbol_enrichment_data_yf,
        get_risk_free_df_yf,
    )


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE_ROOT = REPO_ROOT / "resources" / "option_data"
DEFAULT_TARGET_ROOT = REPO_ROOT / "resources" / "option_data_parquet"
VALID_OPTION_TYPES = {"call", "put"}
DEFAULT_WORKERS = max(1, min((os.cpu_count() or 1) - 1, 8))
logger = logging.getLogger(__name__)


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(processName)s - %(levelname)s - %(message)s",
    )


def _parse_option_folder(folder_name: str) -> tuple[str, str]:
    option_type, separator, expiry = folder_name.partition("_expire=")
    if separator == "" or option_type not in VALID_OPTION_TYPES or not expiry:
        raise ValueError(f"Invalid option folder name: {folder_name}")
    return option_type, expiry


def _csv_files_for_option_folder(option_folder: Path) -> list[Path]:
    data_csv = option_folder / "data.csv"
    if data_csv.exists():
        return [data_csv]
    return sorted(path for path in option_folder.glob("*.csv") if path.is_file())


def _read_option_csvs(csv_paths: list[Path]) -> pd.DataFrame:
    if not csv_paths:
        raise ValueError("No CSV files to convert")

    dataframes = [pd.read_csv(csv_path, sep="\t") for csv_path in csv_paths]
    if len(dataframes) == 1:
        return dataframes[0]
    return pd.concat(dataframes, ignore_index=True)


def _convert_option_folder(
    symbol: str,
    option_folder: Path,
    target_symbol_path: Path,
    stock_history_df: pd.DataFrame,
    risk_free_df: pd.DataFrame,
    dividend_yield: float,
) -> int:
    _configure_logging()
    option_type, expiry = _parse_option_folder(option_folder.name)
    csv_paths = _csv_files_for_option_folder(option_folder)
    if not csv_paths:
        logger.info("Skipping %s %s %s: no CSV files", symbol, expiry, option_type)
        return 0

    option_data = _read_option_csvs(csv_paths)
    option_data = enrich_option_rows(
        option_data,
        symbol=symbol,
        option_type=option_type,
        expiry=expiry,
        stock_history_df=stock_history_df,
        risk_free_df=risk_free_df,
        dividend_yield=dividend_yield,
    )
    target_dir = target_symbol_path / f"expiry={expiry}" / f"type={option_type}"
    target_dir.mkdir(parents=True, exist_ok=True)

    target_file = target_dir / f"{symbol}_{expiry}_{option_type}.parquet"
    option_data.to_parquet(target_file, index=False)
    logger.info("Converted %s %s %s -> %s", symbol, expiry, option_type, target_file)
    return 1


def _convert_option_folders_with_executor(
    symbol: str,
    option_folders: list[Path],
    target_symbol_path: Path,
    stock_history_df: pd.DataFrame,
    risk_free_df: pd.DataFrame,
    dividend_yield: float,
    executor: ProcessPoolExecutor,
) -> int:
    converted_count = 0
    futures = {
        executor.submit(
            _convert_option_folder,
            symbol,
            option_folder,
            target_symbol_path,
            stock_history_df,
            risk_free_df,
            dividend_yield,
        ): option_folder
        for option_folder in option_folders
    }
    for future in as_completed(futures):
        option_folder = futures[future]
        try:
            converted_count += future.result()
        except Exception:
            logger.exception("Failed converting %s/%s", symbol, option_folder.name)
            raise
    return converted_count


def convert_csv_symbol_to_parquet(
    symbol: str,
    source_root: Path | str = DEFAULT_SOURCE_ROOT,
    target_root: Path | str = DEFAULT_TARGET_ROOT,
    clean_target: bool = True,
    risk_free_df: pd.DataFrame | None = None,
    workers: int = DEFAULT_WORKERS,
    executor: ProcessPoolExecutor | None = None,
) -> int:
    source_root = Path(source_root)
    target_root = Path(target_root)
    source_symbol_path = source_root / symbol
    target_symbol_path = target_root / f"symbol={symbol}"

    if not source_symbol_path.exists():
        raise FileNotFoundError(f"No source data found for symbol {symbol}: {source_symbol_path}")

    if target_symbol_path.exists() and not clean_target:
        logger.info("Skipping symbol %s: target path already exists: %s", symbol, target_symbol_path)
        return 0

    option_folders = sorted(path for path in source_symbol_path.iterdir() if path.is_dir())
    logger.info("Starting symbol %s with %d option folder(s)", symbol, len(option_folders))

    stock_history_df, dividend_yield = get_symbol_enrichment_data_yf(symbol)
    if risk_free_df is None:
        risk_free_df = get_risk_free_df_yf()

    if clean_target and target_symbol_path.exists():
        shutil.rmtree(target_symbol_path)

    if workers <= 1 or len(option_folders) <= 1:
        converted_count = sum(
            _convert_option_folder(
                symbol,
                option_folder,
                target_symbol_path,
                stock_history_df,
                risk_free_df,
                dividend_yield,
            )
            for option_folder in option_folders
        )
    else:
        max_workers = min(workers, len(option_folders))
        logger.info("Converting symbol %s with %d worker(s)", symbol, max_workers)
        if executor is None:
            with ProcessPoolExecutor(max_workers=max_workers) as symbol_executor:
                converted_count = _convert_option_folders_with_executor(
                    symbol,
                    option_folders,
                    target_symbol_path,
                    stock_history_df,
                    risk_free_df,
                    dividend_yield,
                    symbol_executor,
                )
        else:
            converted_count = _convert_option_folders_with_executor(
                symbol,
                option_folders,
                target_symbol_path,
                stock_history_df,
                risk_free_df,
                dividend_yield,
                executor,
            )

    logger.info("Finished symbol %s: converted %d option folder(s)", symbol, converted_count)
    return converted_count


def convert_all_csv_symbols_to_parquet(
    source_root: Path | str = DEFAULT_SOURCE_ROOT,
    target_root: Path | str = DEFAULT_TARGET_ROOT,
    clean_target: bool = True,
    workers: int = DEFAULT_WORKERS,
) -> int:
    source_root = Path(source_root)
    target_root = Path(target_root)

    if not source_root.exists():
        raise FileNotFoundError(f"No source option data directory found: {source_root}")

    risk_free_df = get_risk_free_df_yf()
    if clean_target and target_root.exists():
        shutil.rmtree(target_root)

    symbol_paths = sorted(path for path in source_root.iterdir() if path.is_dir())
    total_converted = 0
    if workers <= 1:
        for symbol_path in symbol_paths:
            total_converted += convert_csv_symbol_to_parquet(
                symbol=symbol_path.name,
                source_root=source_root,
                target_root=target_root,
                clean_target=False,
                risk_free_df=risk_free_df,
                workers=workers,
            )
    else:
        max_workers = max(1, workers)
        logger.info("Converting all symbols with one shared pool of %d worker(s)", max_workers)
        with ProcessPoolExecutor(max_workers=max_workers) as executor:
            for symbol_path in symbol_paths:
                total_converted += convert_csv_symbol_to_parquet(
                    symbol=symbol_path.name,
                    source_root=source_root,
                    target_root=target_root,
                    clean_target=False,
                    risk_free_df=risk_free_df,
                    workers=workers,
                    executor=executor,
                )

    logger.info("Finished all symbols: converted %d option folder(s)", total_converted)
    return total_converted


def convert_csv_to_parquet(
    symbol: str | None = None,
    source_root: Path | str = DEFAULT_SOURCE_ROOT,
    target_root: Path | str = DEFAULT_TARGET_ROOT,
    clean_target: bool = True,
    workers: int = DEFAULT_WORKERS,
) -> int:
    _configure_logging()
    if symbol:
        return convert_csv_symbol_to_parquet(
            symbol=symbol,
            source_root=source_root,
            target_root=target_root,
            clean_target=clean_target,
            workers=workers,
        )
    return convert_all_csv_symbols_to_parquet(
        source_root=source_root,
        target_root=target_root,
        clean_target=clean_target,
        workers=workers,
    )


# Backward-compatible aliases for older callers.
convert_symbol = convert_csv_symbol_to_parquet
convert_all_symbols = convert_all_csv_symbols_to_parquet
convert_option_format = convert_csv_to_parquet


def _strip_partition_prefix(path_name: str, prefix: str) -> str:
    if not path_name.startswith(prefix):
        raise ValueError(f"Invalid partition folder name: {path_name}")
    return path_name[len(prefix):]


def _combine_parquet_files(parquet_files: list[Path], output_file: Path) -> int:
    combined_data = pd.concat((pd.read_parquet(path) for path in parquet_files), ignore_index=True)
    temp_file = output_file.with_suffix(".tmp.parquet")
    combined_data.to_parquet(temp_file, index=False)

    for parquet_file in parquet_files:
        if parquet_file != temp_file and parquet_file.exists():
            parquet_file.unlink()

    temp_file.replace(output_file)
    return len(parquet_files)


def combine_expired_parquet_files_for_symbol(
    symbol: str,
    parquet_root: Path | str = DEFAULT_TARGET_ROOT,
    today: date | str | None = None,
) -> int:
    parquet_root = Path(parquet_root)
    symbol_path = parquet_root / f"symbol={symbol}"
    if not symbol_path.exists():
        logger.info("Skipping parquet combine for %s: no target path %s", symbol, symbol_path)
        return 0

    if today is None:
        today_date = date.today()
    elif isinstance(today, str):
        today_date = date.fromisoformat(today)
    else:
        today_date = today

    expiry_paths = []
    for expiry_path in symbol_path.iterdir():
        if not expiry_path.is_dir() or not expiry_path.name.startswith("expiry="):
            continue
        expiry = _strip_partition_prefix(expiry_path.name, "expiry=")
        expiry_date = date.fromisoformat(expiry)
        if expiry_date < today_date:
            expiry_paths.append((expiry_date, expiry, expiry_path))

    combined_count = 0
    for _, expiry, expiry_path in sorted(expiry_paths, reverse=True):
        expiry_had_multiple_files = False
        for option_type in sorted(VALID_OPTION_TYPES):
            type_path = expiry_path / f"type={option_type}"
            if not type_path.exists():
                continue
            parquet_files = sorted(path for path in type_path.glob("*.parquet") if path.is_file())
            if len(parquet_files) <= 1:
                continue

            expiry_had_multiple_files = True
            output_file = type_path / f"{symbol}_{expiry}_{option_type}.parquet"
            combined_file_count = _combine_parquet_files(parquet_files, output_file)
            combined_count += 1
            logger.info(
                "Combined %d parquet file(s) for %s %s %s -> %s",
                combined_file_count,
                symbol,
                expiry,
                option_type,
                output_file,
            )

        if not expiry_had_multiple_files:
            logger.info("Stopping parquet combine for %s at already compacted expiry %s", symbol, expiry)
            break

    return combined_count


def combine_expired_parquet_files(
    parquet_root: Path | str = DEFAULT_TARGET_ROOT,
    today: date | str | None = None,
) -> int:
    _configure_logging()
    parquet_root = Path(parquet_root)
    if not parquet_root.exists():
        return 0

    combined_count = 0
    failed_symbols: list[tuple[str, str]] = []
    for symbol_path in sorted(path for path in parquet_root.iterdir() if path.is_dir()):
        if not symbol_path.name.startswith("symbol="):
            continue
        symbol = _strip_partition_prefix(symbol_path.name, "symbol=")
        try:
            combined_count += combine_expired_parquet_files_for_symbol(symbol, parquet_root, today)
        except Exception as exc:
            logger.exception("Failed parquet combine for symbol %s", symbol)
            failed_symbols.append((symbol, str(exc)))

    logger.info("Finished parquet combine: compacted %d expiry/type folder(s)", combined_count)
    if failed_symbols:
        failed_summary = "; ".join(f"{symbol}: {error}" for symbol, error in failed_symbols)
        raise RuntimeError(
            "Failed combining expired option parquet files for "
            f"{len(failed_symbols)} symbol(s): {failed_summary}. "
            f"Successfully compacted {combined_count} expiry/type folder(s)."
        )
    return combined_count


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert option CSV history files to parquet.")
    parser.add_argument("--symbol", help="Convert only one symbol. Converts all symbols when omitted.")
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE_ROOT)
    parser.add_argument("--target-root", type=Path, default=DEFAULT_TARGET_ROOT)
    parser.add_argument("--no-clean-target", action="store_true", help="Skip symbols that already exist in target.")
    parser.add_argument("--combine-expired", action="store_true", help="Combine expired daily parquet files.")
    parser.add_argument(
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        help=f"Parallel workers per symbol for expiry-level conversion. Default: {DEFAULT_WORKERS}.",
    )
    args = parser.parse_args()

    converted_count = convert_csv_to_parquet(
        symbol=args.symbol,
        source_root=args.source_root,
        target_root=args.target_root,
        clean_target=not args.no_clean_target,
        workers=args.workers,
    )
    combined_count = combine_expired_parquet_files(args.target_root) if args.combine_expired else 0

    print(
        f"Converted {converted_count} option CSV folder(s) to parquet. "
        f"Combined {combined_count} expired expiry/type folder(s)."
    )


if __name__ == "__main__":
    main()
