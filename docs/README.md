# Recorder Technical Documentation

This directory is for technical implementation reference only. It documents architecture, module boundaries, data flows, backend/frontend/Market Pulse internals, scheduling, admin operations, and other details future coding sessions need before changing code.

User-facing introductions and basic run/build notes belong in the module `README.md` files outside this directory. Do not move product introductions, marketing copy, or general user onboarding into `/docs`.

## Module Docs

- [System Architecture](./system-architecture.md): repository layout, runtime services, frameworks, third-party libraries, and cross-service boundaries.
- [Recorder Backend](./recorder-backend.md): Spring Boot API, persistence model, security, records, files, alerts, stock data, option data, LLM record helpers, scheduled market-news records, and Qdrant embedding integration.
- [Recorder Frontend](./recorder-frontend.md): Vue application structure, routes, stores, API clients, record workflows, LLM chat/admin tools, portfolio charts, calendar, and option tools.
- [Market Pulse](./market-pulse.md): FastAPI service, stock/option data collection, parquet readers/converters, configurable LLM client, yfinance news summaries, Qdrant vector search, and task entry points.
- [Data Flows](./data-flows.md): end-to-end flows for login, record CRUD, file upload/download, semantic search, related records, LLM labels/chat records, market-news records, portfolio charting, option history, and stock history refresh.
- [Scheduling And Admin Operations](./scheduling-and-admin.md): current DB-backed admin job system, user record alerts, dashboard behavior, Qdrant background jobs, and timezone rules.

## Runtime Modules

The repository has three application modules:

- `recorder-backend`: Java 17 / Spring Boot backend and API gateway for app data.
- `recorder-frontend`: Vue 3 SPA served during development by Vue CLI and in production as static assets.
- `market_pulse`: Python / FastAPI service for market data, option data, LLM access/news summaries, and Qdrant-backed semantic search.

The root `docker-compose.yml` runs `qdrant`, `market-pulse`, and `recorder-backend`. The backend depends on Market Pulse health and serves the API consumed by the frontend.

## Before Changing Code

For most future sessions, read these first:

1. `system-architecture.md` for service boundaries and module ownership.
2. `scheduling-and-admin.md` before touching jobs, alerts, admin tools, Qdrant background work, or timezone-sensitive schedule logic.
3. The module doc for the code you are changing.
