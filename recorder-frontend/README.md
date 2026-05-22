# Recorder Frontend

Recorder Frontend is the browser app for Recorder. It provides the user interface for records, labels, file attachments, calendar views, portfolio tools, option tools, scheduled record alerts, account pages, LLM chat, and admin tools.

This README is intentionally a short module introduction. Technical route maps, stores, API clients, component responsibilities, and implementation details are documented under [`../docs`](../docs/README.md).

## Development

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run serve
```

Build production assets:

```bash
npm run build
```

The production build is copied into the backend static resources by the root `build.sh` script.

## Technical Reference

For frontend implementation details, read:

- [`../docs/recorder-frontend.md`](../docs/recorder-frontend.md)
- [`../docs/data-flows.md`](../docs/data-flows.md)
