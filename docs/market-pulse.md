# Market Pulse

## Module Summary

`market_pulse` is a Python service that provides stock price history, option history, option parquet maintenance, Fear & Greed data, configurable OpenAI-compatible LLM access, yfinance news summaries, and Qdrant-backed semantic search. It is exposed as a FastAPI app and consumed primarily by `recorder-backend`.

## Frameworks And Libraries

- FastAPI, Starlette, Uvicorn: HTTP API.
- Pydantic: request models.
- pandas, numpy: data transformation.
- yfinance: stock/option source data.
- fastparquet, cramjam: parquet storage and compression.
- qdrant-client: vector database client.
- sentence-transformers: embedding model.
- httpx, PyYAML: configurable OpenAI-compatible LLM calls.
- requests, beautifulsoup4: external data fetch/parsing utilities.
- matplotlib, mplfinance: plotting utilities.

## Application Entry Points

- `main/api/app.py`
  - Creates `FastAPI()`.
  - Includes routers:
    - `stock_api.router`
    - `qdrant_api.router`
    - `option_api.router`
    - `news_api.router`
    - `llm_api.router`
- `main/main.py`
  - CLI-oriented entry point for stock and market tasks.
  - Imports `update_stock_data`, flexible stock update, current minute price, daily history, trade-day check, and Fear & Greed helpers.
- `run_market_pulse_server.sh`
  - Local server startup helper.
- `Dockerfile`
  - Containerizes the FastAPI service.

## API Layer

### `main/api/stock_api.py`

Endpoints:

- `GET /health`
  - Output: service health response.
- `GET /option-symbols`
  - Output: `{ symbols: string[] }` from default option symbol file.
- `GET /stock-symbols`
  - Output: `{ symbols: string[] }` from default stock symbol file.
- `POST /update-stock-data`
  - Input: `UpdateStockDataRequest`
    - `jobName`
    - `symbolPath`
    - `optionSymbolPath`
    - `symbols`
    - `optionSymbols`
    - `taskLabel`
    - `maxWorkers`
    - `update_most_recent_date`
  - Output: task result or command output string.
  - Flow: chooses stock update task from request and runs yfinance/parquet update logic.
- `POST /current-minute-prices`
  - Input: `SymbolsRequest { symbols }`.
  - Output: JSON parsed current minute stock prices.
- `POST /stock-daily-history`
  - Input: `SymbolsRequest { symbols }`.
  - Output: JSON parsed daily history, shaped with top-level `Data`.
- `GET /fear-greed-index`
  - Input: optional update flag.
  - Output: CNN Fear & Greed data.
- `GET /today-is-trade-day`
  - Output: boolean trade-day status.

### `main/api/option_api.py`

Base router prefix: `/options`.

Endpoints:

- `POST /symbols`
  - Input: empty JSON object.
  - Output: `{ symbols: string[] }`.
  - Flow: reads tracked option parquet symbols.
- `POST /expiry-dates`
  - Input: `{ symbol }`.
  - Output: `{ expiry_dates: string[] }`.
  - Flow: scans parquet partitions/folders for expiries.
- `POST /history`
  - Input: `{ symbol, expiry, option_type }`.
  - Output: option history grouped by strike.
  - Flow: reads parquet data for requested symbol/expiry/type.
- `GET /convert-csv-to-parquet`
  - Input: optional `source_data_folder_name`.
  - Output: conversion summary.
  - Flow: converts option CSV folder structure to partitioned parquet.
- `GET /combine-expired-parquet`
  - Output: `OptionParquetCombineResponse`.
  - Flow: combines expired per-date parquet files into consolidated expired partitions.

### `main/api/qdrant_api.py`

Base router prefix: `/qdrant`.

Endpoints:

- `POST /upsert`
  - Input: `UpsertRequest { record_id, user_id, is_public, text, labels, collection }`.
  - Output: point ids.
  - Flow: chunks record text, embeds chunks, upserts vectors with ACL payload.
- `POST /query`
  - Input: `QueryRequest { user_id, query_text, similarity_threshold, limit, collection }`.
  - Output: search results grouped by record id with `best_score` and `chunks`.
  - Flow: embeds query, searches Qdrant with ACL filter for public records or matching user id, then aggregates chunk hits by `record_id`. The returned `chunks` are the stored chunk texts from Qdrant payloads, not full record bodies.
- `POST /delete`
  - Input: `DeleteRequest { record_id, collection }`.
  - Output: deletion status.
- `POST /exists`
  - Input: `ExistsRequest { record_id, collection }`.
  - Output: `{ exists: boolean }`.

## Data Source Layer

### `main/data_source/data_source.py`

Classes:

- `StockPriceData`
  - Owns yfinance-based stock history/current data retrieval.
  - Produces DataFrames and JSON-friendly data for API tasks.
- `StockOptionData`
  - Owns yfinance option chain/history retrieval and storage.
  - Coordinates option symbols, expiration dates, option rows, and optional enrichment.

Functions:

- `get_symbols_from_file(symbols_file_path) -> list[str]`: reads tracked symbols.
- `is_today_trade_day_yf() -> bool`: determines trade day from yfinance market data.
- `get_current_minute_stock_price_json(symbols) -> str`: returns recent minute data as JSON text.
- `get_stock_price_day_history_json(symbols) -> str`: returns historical daily OHLCV as JSON text.
- `get_fear_greed_index_cnn(update_file=False) -> dict/str`: fetches or reads CNN Fear & Greed data.

### `main/tasks/update_stock_data.py`

Functions:

- `update_stock_data(update_previous_trade_date=False, ...)`
  - Standard stock/option update workflow for configured symbol files.
- `update_stock_data_flexible(symbols=None, symbols_path=None, task_label="close", update_stock_data=True, ...)`
  - Flexible worker-based update flow with caller-provided symbols and task label.
- `get_current_minute_stock_price_json_task(symbols)`
  - API task wrapper for minute prices.
- `get_stock_price_day_history_json_task(symbols)`
  - API task wrapper for daily history.
- `get_fear_greed_index_data()`
  - API task wrapper for Fear & Greed data.
- `get_today_is_trade_day()`
  - Trade-day helper.
- `get_default_option_symbols_from_file()`, `get_default_stock_symbols_from_file()`
  - Read default resource symbol lists.

### `main/data_source/option_parquet_reader.py`

Class:

- `OptionParquetReader`
  - Reads partitioned option parquet data under resources.
  - Main methods include symbol discovery, expiry discovery, and history extraction.
  - Converts pandas/numpy values to JSON-safe Python values.

Top-level helpers:

- `get_all_tracked_symbols(option_parquet_root) -> list[str]`.
- `get_expiry_dates(symbol, option_parquet_root, ...) -> list[str]`.
- `get_option_history(symbol, expiry, option_type, ...) -> OptionHistory`.

### `main/data_source/option_format_conversion.py`

Functions:

- `convert_csv_symbol_to_parquet(...)`
  - Converts one symbol’s option CSV folders to parquet partitions.
- `convert_all_csv_symbols_to_parquet(...)`
  - Batch conversion across symbols with worker processes.
- `convert_csv_to_parquet(...)`
  - Lower-level CSV to parquet conversion.
- `combine_expired_parquet_files_for_symbol(...)`
  - Combines expired parquet files for a single symbol.
- `combine_expired_parquet_files(...)`
  - Batch combine task used by API.

### `main/data_source/option_enrichment.py`

Functions:

- `get_daily_history_df(symbol, start=None, ticker=None) -> DataFrame`: stock daily close source.
- `read_option_to_dfs(...) -> list[DataFrame]`: loads option CSV data into DataFrames.
- Black-Scholes helpers: `black_scholes_delta`, `gamma`, `theta`, `vega`, `rho`.
- Column enrichers: `add_delta_column`, `add_gamma_column`, `add_theta_column`, `add_vega_column`, `add_rho_column`, `add_days_to_expiry`.
- Market context helpers: `get_dividend_rate_yf`, `get_symbol_enrichment_data_yf`, `get_risk_free_df_yf`.
- `enrich_option_rows(...)`: merges stock/rate/dividend context and adds Greeks.

### `main/data_source/option_parquet_schema.py`

TypedDict response schemas:

- `TrackedSymbol`
- `OptionExpiry`
- `StrikeHistory`
- `OptionHistory`

## Qdrant And Embeddings

### `main/ml/qdrant_embeddings.py`

Functions:

- `ensure_collection(client, collection, vector_dim)`: creates collection if needed.
- `embed_text(model, text) -> list[float]`: transforms text into embedding vector.
- `chunk_by_words(text, chunk_words, overlap_words) -> list[str]`: splits long record text for vector storage.
- `_acl_filter(user_id) -> Filter`: allows public records or records owned by user.
- `search_top_k(...)`: low-level vector search.
- `search_distinct_record_ids(...)`: groups chunk hits into record-level results.
- `get_qdrant_client() -> QdrantClient`: builds client from env/defaults.
- `get_embed_model() -> SentenceTransformer`: loads cached embedding model.
- `search_record_ids_by_similarity(...)`: API-facing search function.
- `delete_vectors_by_record_id(...)`: deletes all chunks for record id.
- `upsert_vectors_by_record_id(...)`: chunks, embeds, and upserts record text.
- `record_exists(...) -> bool`: checks whether any vector exists for record id.

Input payload fields:

- `record_id`: backend `Record.id`.
- `user_id`: backend `User.id` or username fallback.
- `is_public`: record visibility flag.
- `text`: record embedding string: title + labels + content.
- `labels`: record label names.
- `collection`: Qdrant collection name.

Output shape:

- Upsert returns point ids.
- Query returns record ids, owner/public metadata, best similarity score, and matched chunk text. Backend LLM chat uses these chunks as retrieval context and performs a DB visibility/date check before sending them to an LLM.

### `main/api/llm_api.py`

Base router prefix: `/llm`.

Endpoints:

- `POST /chat`
  - Input: `{ messages, temperature?, max_tokens? }`.
  - Output: `{ reply }`.
  - Flow: forwards OpenAI-style chat messages to the configured LLM provider through `main/llm/client.py`.
  - Errors: returns HTTP 503 with `No LLM connection` when the configured model endpoint is unavailable.

### `main/api/news_api.py`

Base router prefix: `/news`.

Endpoints:

- `POST /stock-summary`
  - Input: `{ symbols?, max_news_per_symbol?, max_workers? }`.
  - Output: `{ generated_at, summaries: [{ symbol, summary, article_count, articles }] }`.
  - Flow: reads default news symbols from `resources/symbols/news_symbols.txt` when symbols are omitted, fetches `yf.Ticker(symbol).news`, normalizes articles, and uses the LLM client to create one paragraph per symbol.
  - Concurrency: symbols are processed with a bounded `ThreadPoolExecutor` so yfinance fetches and LLM summaries can run in parallel.

## LLM Client

### `main/llm/client.py`

Responsibilities:

- Loads config from `resources/config/llm.yaml`.
- Supports env overrides such as `LLM_PROVIDER`, `LLM_MODEL`, `LLM_BASE_URL`, `LLM_CHAT_COMPLETION_PATH`, `LLM_API_KEY`, `LLM_API_KEY_ENV`, `LLM_TIMEOUT_SECONDS`, `LLM_TEMPERATURE`, and `LLM_MAX_TOKENS`.
- Supports fallback env overrides with `LLM_FALLBACK_*` names. `LLM_FALLBACK_API_KEY_ENV` must be an environment variable name such as `LLM_API_KEY`, not the literal key value.
- Defaults to LM Studio/OpenAI-compatible local API:
  - provider: `lmstudio`
  - base URL: `http://localhost:1234/v1`
  - endpoint: `/chat/completions`
  - auth: none when `api_key_env` is empty.
- `llm.yaml` is intended to hold placeholder/default values only. Runtime deployments should override provider, model, base URL, path, and key through `.env`.
- `LLM_API_KEY` can be used directly for bearer auth. `LLM_API_KEY_ENV` is still supported when the config should name a different environment variable.
- `LLM_BASE_URL` should include the OpenAI-compatible version prefix, for example `http://192.168.1.158:1234/v1`.
- When running inside Docker, rewrites only `localhost`/`127.0.0.1` LLM base URLs to `host.docker.internal` unless `LLM_ALLOW_DOCKER_LOCALHOST` is truthy. LAN IPs such as `192.168.x.x` are used directly.
- Resolves placeholder model `local-model` by calling `/models` and using the first model id.
- Treats known endpoint-error text such as `Unexpected endpoint or method ... Returning 200 anyway` as an LLM failure even when the HTTP status is 200.
- HTTP errors include a short response-body excerpt in `LlmClientError` to help debug model/server-specific 400 responses.

Main helpers:

- `load_llm_config(config_path=None) -> LlmConfig`
- `chat_completion(messages, temperature=None, max_tokens=None) -> str`
- `summarize_text(text, prompt=..., temperature=None, max_tokens=None) -> str`

### `main/news/stock_news.py`

Responsibilities:

- `get_symbol_news(symbol, max_news)`: calls `yf.Ticker(symbol).news`.
- `normalize_news_item(item)`: normalizes yfinance news shape to title, publisher/source, link, publish time, and summary/snippet.
- `get_default_news_symbols_from_file()`: reads default news symbols from `resources/symbols/news_symbols.txt`; stock-data symbol refresh still uses `symbols.txt`.
- `summarize_symbol_news(symbol, articles)`: creates a one-paragraph LLM summary. It strips `<think>...</think>`, markdown fences, and prefixes like `Summary:` from model output. If the LLM fails or returns only unusable reasoning/empty content, it falls back to a deterministic summary from article titles/summaries.
- `build_llm_article_text(articles)`: bounds prompt input for news summaries. Env knobs: `NEWS_LLM_MAX_ARTICLES`, `NEWS_LLM_MAX_TITLE_CHARS`, `NEWS_LLM_MAX_SUMMARY_CHARS`, and `NEWS_LLM_MAX_PROMPT_CHARS`.
- `get_stock_news_summaries(symbols=None, max_news_per_symbol=5, max_workers=4)`: cleans symbols, sorts only at backend record creation time, processes summaries concurrently, and returns API response data.

## Utility Layer

- `main/utils/path_utils.py`
  - `get_resources_path(*subpaths)`: returns paths under Market Pulse resources.
  - `combine_existing_option_files(option_path_name)`: utility for combining option files.
- `main/utils/logger_utils.py`
  - `setup_logging(logger_name, log_file_name="log.txt")`: rotating file logger under resources logs.
- `main/api/logging_config.py`
  - Adds timestamp formatting to Uvicorn log handlers.
  - Filters Uvicorn access logs for internal `/health` checks so Docker/backend health polling does not fill the logs.
- `main/utils/plot_utils.py`
  - `plot_stock(...)`: candlestick/stock plotting.
  - `plot_stock_with_option(...)`: overlays stock and option data.

## Resource Inputs And Outputs

Inputs:

- `resources/symbols/symbols.txt`: default stock symbols.
- `resources/symbols/news_symbols.txt`: default market-news summary symbols.
- `resources/symbols/option_symbols.txt`: default option symbols.
- Option CSV/parquet folders under resources.
- Qdrant storage mounted through Docker.
- yfinance live market data.

Outputs:

- JSON stock/option API responses.
- Local stock data database/resource files.
- Partitioned option parquet files.
- Qdrant vector points.
- Rotating logs in `resources/logs`.
