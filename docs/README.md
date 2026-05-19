# Recorder Technical Documentation

This directory documents the project at module and file/class level so future work can start from the technical flow instead of reading every source file.

## Module Docs

- [System Architecture](./system-architecture.md): repository layout, runtime services, frameworks, third-party libraries, and cross-service boundaries.
- [Recorder Backend](./recorder-backend.md): Spring Boot API, persistence model, security, records, files, alerts, stock data, option data, LLM record helpers, scheduled market-news records, and Qdrant embedding integration.
- [Recorder Frontend](./recorder-frontend.md): Vue application structure, routes, stores, API clients, record workflows, LLM chat/admin tools, portfolio charts, calendar, and option tools.
- [Market Pulse](./market-pulse.md): FastAPI service, stock/option data collection, parquet readers/converters, configurable LLM client, yfinance news summaries, Qdrant vector search, and task entry points.
- [Data Flows](./data-flows.md): end-to-end flows for login, record CRUD, file upload/download, semantic search, related records, LLM labels/chat records, market-news records, portfolio charting, option history, and stock history refresh.

## Runtime Modules

The repository has three application modules:

- `recorder-backend`: Java 17 / Spring Boot backend and API gateway for app data.
- `recorder-frontend`: Vue 3 SPA served during development by Vue CLI and in production as static assets.
- `market_pulse`: Python / FastAPI service for market data, option data, LLM access/news summaries, and Qdrant-backed semantic search.

The root `docker-compose.yml` runs `qdrant`, `market-pulse`, and `recorder-backend`. The backend depends on Market Pulse health and serves the API consumed by the frontend.
