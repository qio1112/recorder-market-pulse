# Recorder Backend

Recorder Backend is the server module for the Recorder app. It handles login, records, files, labels, alerts, admin tools, stock/option data access, email notifications, and integration with Market Pulse and Qdrant.

This README is intentionally a short module introduction. Technical architecture, APIs, entities, scheduling behavior, and implementation details are documented under [`../docs`](../docs/README.md).

## What It Does

- Serves authenticated Recorder APIs.
- Stores records, labels, files, user accounts, alerts, job status, and stock history in MySQL.
- Sends signup, password reset, record alert, and admin job emails.
- Serves the built frontend assets in production.
- Calls Market Pulse for stock data, option data, LLM, and Qdrant vector operations.

## Run And Build

From the repository root:

```bash
./build.sh --build 1,2 --run-docker-compose true
```

For a backend-only compile:

```bash
mvn -pl recorder-backend -am compile -DskipTests
```

## Configuration

Runtime settings come from the root `.env` file and `src/main/resources/application.properties`. Common required values include database credentials, JWT secret, mail settings, upload/log paths, Market Pulse URL, and app timezone.

For detailed backend behavior, start with:

- [`../docs/recorder-backend.md`](../docs/recorder-backend.md)
- [`../docs/scheduling-and-admin.md`](../docs/scheduling-and-admin.md)
