# System Architecture

## Purpose

Recorder is a personal record management application with attachments, labels, alerts, portfolio tracking, option analysis, semantic record search, and LLM-assisted record workflows. The application is split into a Spring Boot backend, Vue frontend, and Python Market Pulse service.

## Repository Layout

- `recorder-backend/`: Spring Boot API, JPA entities, repositories, services, controllers, static frontend assets, uploaded file storage, and backend Dockerfile.
- `recorder-frontend/`: Vue 3 SPA source, Vuex modules, route definitions, API client wrappers, and charting UI.
- `market_pulse/`: FastAPI service and scripts for yfinance stock data, option CSV/parquet processing, Qdrant embeddings, and market utilities.
- `docker-compose.yml`: starts Qdrant, Market Pulse, and backend services with shared volumes and environment variables.
- `pom.xml`: Maven parent project for `recorder-backend`.
- `build.sh`: build/deployment helper script.

## Runtime Services

| Service | Technology | Main Role | External/Storage Dependencies |
| --- | --- | --- | --- |
| Frontend | Vue 3, Vue Router, Vuex, Axios, ECharts, FullCalendar | Browser SPA for records, calendar, portfolio, options, LLM chat, admin tools, and account pages | Calls backend `/api` |
| Backend | Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA | Authenticated API, business logic, database persistence, file storage, gateway to Market Pulse and LLM helpers | MySQL, filesystem uploads, SMTP, Market Pulse |
| Market Pulse | Python, FastAPI, pandas, yfinance, Qdrant client, sentence-transformers, httpx | Market data acquisition, option history/parquet APIs, semantic vector search, LLM proxy, news summaries | yfinance, local resources, Qdrant, LM Studio/OpenAI-compatible LLM |
| Qdrant | qdrant/qdrant Docker image | Vector store for record text chunks | Persistent volume |

## Main Technical Solutions

- Authentication uses stateless JWT. The backend issues tokens from `/api/auth/authenticate`; the frontend stores the token in `localStorage`; Axios adds `Authorization: Bearer <token>` on each request.
- Authorization is enforced in backend controllers/services with the current authenticated user. Public records are readable by other users; private records are owner-only unless the user is admin.
- Record persistence uses Spring Data JPA entities and repositories. Labels are many-to-many with records, files are associated through `RecFile`, metadata is stored as JSON text through `StringMapJsonConverter`.
- File uploads use multipart requests. Backend writes binary files to `recfile.upload.dir` and stores metadata in the database.
- Semantic search uses Market Pulse `/qdrant` APIs. Backend builds an embedding string from record title, labels, and content, then queues durable Qdrant upsert/delete jobs after user record changes.
- Related records reuse the same Qdrant embeddings. The backend queries with a source record’s embedding string, excludes the source record, rechecks visibility, and the frontend loads related results asynchronously after the main detail view.
- Admin LLM chat supports two record-context modes: legacy eager Qdrant enrichment and `RECORD_AGENT`, a plain-text tool loop that lets the model request the `search_records` tool when needed. Both modes reuse backend visibility/recency filtering before any record chunks are sent to the LLM.
- LLM access is centralized in Market Pulse through an OpenAI-compatible client. Runtime LLM settings come from `.env` (`LLM_PROVIDER`, `LLM_BASE_URL`, `LLM_MODEL`, `LLM_CHAT_COMPLETION_PATH`, `LLM_API_KEY`), while `llm.yaml` holds placeholders/defaults. Docker runtime rewrites only `localhost` or `127.0.0.1` LLM URLs to `host.docker.internal`; LAN IP base URLs are used directly.
- The LLM client supports `LLM_FALLBACK_*` settings. Fallback API key env values should name another env var, for example `LLM_FALLBACK_API_KEY_ENV=LLM_API_KEY`.
- Backend LLM helpers use the Market Pulse chat endpoint for admin-only chat, record label generation, chat summarization, chat-to-record creation, and market-news summaries. Built-in prompt text and Java-side output token budgets are centralized under `com.yipeng.recorder.prompt`.
- Stock and option data are delegated to Market Pulse. Backend exposes user-facing endpoints, validates auth/admin access, normalizes inputs, and persists stock daily history to the database.
- Daily market-news summaries use yfinance news plus the LLM. Market Pulse reads default symbols from `resources/symbols/news_symbols.txt`, bounds news prompt size through `NEWS_LLM_*` env values, cleans reasoning/markdown from model output, and falls back to article text when the LLM fails. Backend creates public records in 5-symbol chunks, with date/market labels and uppercase symbol labels.
- Scheduled stock-data jobs run weekdays only. When a schedule fires multiple stock jobs together, backend sends one combined email instead of one email per job.
- A database-backed job framework handles admin-visible scheduled/manual jobs. Built-in job definitions are seeded into MySQL on startup, execution history is stored in `job_execution`, and the active DB scheduler poller replaces the old hardcoded `CronService` scheduled methods. Qdrant maintenance includes hidden async upsert/delete jobs plus visible consistency/datafix jobs.
- User record alerts use a separate database-backed scheduler with `alert_schedule` and `alert_execution`. They are not admin jobs: normal users can create alerts on their own records, admins can view all active alert schedules, and recurring alert clock times must be interpreted in `application.time-zone`.
- Portfolio charts are computed in the frontend from investment records plus cached stock history. The backend stores raw trade records and historical market data; `portfolioUtils.js` computes holdings, cash, cost basis, realized/unrealized PnL, and aggregate series.

## Third-Party Libraries

Backend:

- Spring Boot starter web/security/data-jpa/validation/mail/test.
- JJWT for JWT creation and validation.
- MySQL Connector/J for database access.
- Apache Commons Lang for string utilities.
- RestAssured and Spring Security Test for tests.

Frontend:

- Vue 3 and Vue CLI.
- Vue Router for hash routes and navigation guards.
- Vuex for user, portfolio, records, and option-history state.
- Axios for HTTP requests.
- ECharts and `vue-echarts` for portfolio and option charts.
- FullCalendar for calendar page rendering.

Market Pulse:

- FastAPI/Uvicorn for HTTP APIs.
- pandas/numpy/yfinance for market data.
- fastparquet/cramjam for parquet storage.
- qdrant-client and sentence-transformers for vector embeddings and search.
- httpx/PyYAML for configurable LLM provider calls.
- matplotlib/mplfinance for plotting utilities.
- requests/beautifulsoup4 for external data scraping utilities.

## Service Boundary

The frontend should call only backend `/api` endpoints. The backend is the application authority for authentication, authorization, records, files, labels, and persisted stock history. Market Pulse is treated as an internal data/ML service and is accessed by backend services through `MarketPulseApiService` and `QdrantEmbeddingService`. LLM calls also travel through backend APIs first, then through Market Pulse, so the browser never talks directly to LM Studio or any external LLM provider.

## Operational Reference

Background work is documented in [Scheduling And Admin Operations](./scheduling-and-admin.md). Use it as the starting point for:

- admin job config/execution tables,
- built-in job seeding,
- record alert scheduling,
- Qdrant async upsert/delete jobs,
- admin dashboard grouping,
- schedule timezone behavior.
