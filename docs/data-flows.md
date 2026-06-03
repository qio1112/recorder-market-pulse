# Data Flows

## Authentication Flow

1. User submits username/password from `LoginPage.vue`.
2. Vuex `user/authenticateUser` calls `UserService.authenticate`.
3. `UserService.authenticate` posts to backend `POST /api/auth/authenticate`.
4. `AuthenticationController` authenticates through Spring Security and returns `{ token }`.
5. Frontend stores token in `localStorage`.
6. `api/http.js` attaches `Authorization: Bearer <token>` to future API calls.
7. Backend `JwtRequestFilter` validates the token and sets the Spring security context.

Inputs:

- `{ username, password }`.

Outputs:

- JWT token in frontend state/local storage.
- Authenticated backend request context for protected APIs.

## Record Create Flow

1. `AddNewRecord.vue` collects form data through `RecordEditForm.vue`.
2. Frontend creates `AddRecordRequest`.
3. `RecordService.addNewRecord` sends multipart form:
   - JSON part `newRecordRequest`.
   - Optional `images`.
   - Optional `files`.
4. Backend `RecordController.createRecord` resolves authenticated user.
5. Controller writes uploaded files to `recfile.upload.dir` and creates `RecFile` entities.
6. `RecordService.createRecord` creates labels, enriches investment labels from metadata, saves files/record, and schedules alert if present.
7. `QdrantJobService.queueUpsert` creates a durable `QDRANT_RECORD_UPSERT` execution.
8. Backend returns created `Record`.

Inputs:

- Title, content, labels, public flag, metadata, optional alert fields, optional files/images. When an alert is selected, the form sends an ISO offset timestamp and includes the `ALERT` label.
- Optional LLM-generated labels from `POST /api/llm/record-labels`.

Outputs:

- `Record` row, label associations, file metadata, binary files, optional DB-backed alert schedule, optional Qdrant vectors.

## Record Alert Scheduling Flow

1. `RecordEditForm.vue` collects alert type, date/time, and recurring weekdays.
2. The form sends `alertTime` as an ISO timestamp with offset and includes the `ALERT` label when an alert is selected.
3. `RecordController` creates an `AlertSchedule` only when `ALERT`, `alertType`, and required alert fields are present.
4. `RecordService` attaches the alert to the saved record and calls `ScheduleAlertService.scheduleAlert`.
5. `ScheduleAlertService` computes `nextRunAt`, sets `enabled`, clears `lastError`, and saves `alert_schedule`.
6. The scheduler poller checks due `next_run_at` rows every 5 seconds by default.
7. When due, it creates an `alert_execution` row, sends email, then marks the execution `SUCCESS` or `FAILED`.
8. One-time alerts are disabled after success. Recurring alerts roll forward to the next selected weekday.
9. `/tools/scheduled-records` calls `GET /api/records/alert-schedules` to show active schedules only.

Timezone notes:

- The app timezone is `application.time-zone` / `APP_TIMEZONE`, currently `America/New_York`.
- MySQL/JPA may reload a sent `15:30-04:00` timestamp as `19:30Z`. Recurring schedules must convert stored timestamps back to app timezone before extracting hour/minute.
- Backend schedule-list responses and frontend display code both normalize alert times to app timezone.

## Record Update Flow

1. `EditRecord.vue` loads existing record with `GET /api/records/record/{id}`.
2. User edits fields in `RecordEditForm.vue`.
3. Frontend creates `EditRecordRequest`.
4. `RecordService.editRecord` sends multipart form:
   - JSON part `updateRecordRequest`.
   - Optional new files/images.
5. Backend checks record existence and modify permission.
6. `RecordService.updateRecord` updates labels, metadata, files, alert schedule row/next run, and modification time.
7. Removed file ids are deleted from DB and removed from disk.
8. A durable `QDRANT_RECORD_UPSERT` execution refreshes vectors with the updated embedding string.

Inputs:

- Record id, updated fields, labels, metadata, removeFileIDs, new files, alert changes.

Outputs:

- Updated `Record`, updated file/label/alert state, refreshed Qdrant vectors.

## Record List And Filter Flow

1. `RecordsFilter.vue` builds `ListRecordRequest` from UI state.
2. `RecordsList.vue` calls `getRecords`.
3. Backend `POST /api/records/list-records` receives filters.
4. `RecordService.listRecordsCoreDataWithFilter` normalizes pagination/sort/date filters.
5. `RecordRepository.filterRecords` returns a `Page<Record>` that respects user visibility and admin status.
6. Frontend renders `RecordPreview.vue` items.

Inputs:

- Labels, excluded labels, title text, date ranges, public filter, owner-only flag, page, page size, sort.

Outputs:

- Paged record summaries.

## Semantic Record Search Flow

1. User enters text query in records UI.
2. Frontend creates `ListDescribedRecordRequest`.
3. `RecordService.getTextQueryRecords` posts to `/api/records/get-records-by-description`.
4. Backend sets query user id from authenticated user and calls `QdrantEmbeddingService.querySimilarRecords`.
5. Market Pulse `/qdrant/query` embeds query text, searches Qdrant with ACL filter, and returns record ids/scores.
6. Backend filters scores by threshold, loads records from DB, rechecks visibility, and returns records.

Inputs:

- Query text, optional limit and similarity threshold.

Outputs:

- Visible records sorted by similarity.

## Related Records Flow

1. `RecordDetail.vue` loads the main record through `GET /api/records/record/{id}`.
2. The page renders the record detail first.
3. `RecordDetail.vue` then calls `getRelatedRecords(record.id)` in a separate async path.
4. Backend `GET /api/records/record/{id}/related` loads the source record and checks visibility.
5. Backend calls Qdrant through `QdrantEmbeddingService.querySimilarRecords` using `record.getEmbeddingString()`.
6. Backend excludes the source record, rechecks visibility on each candidate, sorts by score, and returns up to 5 results.
7. Frontend displays a separate Related Records box with its own loading/error state.

Inputs:

- Source record id.

Outputs:

- Related record previews with similarity score/chunk metadata available in the DTO.

## Admin LLM Chat Flow

1. Admin sends a message from `/tools/llm-chat`.
2. Frontend posts API-safe messages plus `chatMode` to `POST /api/llm/chat`.
3. Backend validates admin access and routes by mode:
   - `RELATED_CONTEXT`: old eager Qdrant enrichment through `LlmRecordService.enrichChatWithRelatedChunks`, then one Market Pulse `/llm/chat` call.
   - `RECORD_AGENT`: bounded `LlmAgentService` flow with registered tools. V1 has only `search_records`.
4. Both paths use `RelatedRecordContextService` for Qdrant record retrieval rules: DB visibility recheck, recent-record preference, historical-query allowance, high-score old-record allowance, and bounded chunk output.
5. Agent mode builds a system prompt from registered tool metadata. The model can answer directly or request `search_records` with either `queries: [...]` or legacy `query`.
6. Backend executes at most one records search per user message. The tool can search 1-5 Qdrant keywords, merge/dedupe results, and return up to 10 compact chunks.
7. If no chunks are found, backend immediately returns the no-records message. If chunks are found, backend makes one final-answer-only LLM synthesis call.
8. If the final synthesis is blank, malformed, random-looking, or asks for another tool, backend returns the "records found but model failed" fallback instead of claiming no related records.

Inputs:

- Current chat messages, authenticated admin user, and selected `chatMode`.
- Optional Qdrant chunk context from visible records.

Outputs:

- `{ reply, toolUsages? }` from Market Pulse/agent orchestration. `toolUsages` is optional UI metadata such as searched Qdrant keywords. Chat history stays in the browser if a transient LLM failure occurs.

## LLM Label Generation Flow

1. `RecordEditForm.vue` checks LLM availability through `POST /api/llm/chat`.
2. If the LLM is connected, the form shows `Generate Labels`.
3. User clicks the button; frontend posts `{ title, content, maxLabels }` to `POST /api/llm/record-labels`.
4. Backend `LlmController` validates admin access.
5. `LlmRecordService.generateLabels` prompts the LLM for short key labels with a larger output budget and no-reasoning/no-markdown instructions.
6. Backend parses labels from JSON arrays, quoted strings, comma/newline lists, or label-like tokens after stripping reasoning/markdown. If unusable, it derives fallback labels from record title/content.
7. Backend normalizes labels to uppercase, compact underscore-separated values, removes generic labels/stop words, and caps count/length.
8. Frontend merges returned labels into the form.

Inputs:

- Record title/content and max label count.

Outputs:

- Short labels suitable for saving with the record.

## LLM Chat Record Flow

1. Admin opens `/tools/llm-chat`.
2. The page checks LLM availability; when connected, admin can chat with the configured model.
3. Visible chat messages are stored in local storage and cleared by the start-new-chat button or invalid auth.
4. Admin clicks `Add Chat As Record`.
5. Frontend posts visible non-system messages to `POST /api/llm/chat-record`.
6. Backend returns `202 Accepted` with `{ status: "started", message }` and starts `LlmRecordService.createChatRecordAsync`.
7. The async job summarizes the chat, asks the LLM for a one-line short title, and generates up to 5 labels.
8. Backend strips reasoning/markdown/prose from title/label outputs and derives fallback title/labels when model output is unusable.
9. Backend creates a record whose content is the generated summary only. The transcript is input for summary/title/labels but is not saved in the record body.
10. `QdrantJobService.queueUpsert` queues durable Qdrant indexing for the new record.

Inputs:

- Current chat messages.

Outputs:

- Immediate job-start response to the frontend.
- Eventually, a new record with LLM/fallback title, summary-only content, labels, and Qdrant vectors.

## File Download Flow

1. Record UI requests a file blob through `getRecFile(fileID)`.
2. Frontend calls `GET /api/recfile/{fileID}`.
3. Backend `RecFileController` loads `RecFile`, checks visibility, resolves filesystem path.
4. Backend returns `UrlResource` with probed content type.
5. Frontend creates object URL for image preview or download/display.

Inputs:

- File id.

Outputs:

- Binary blob.

## Calendar Flow

1. `CalendarPage.vue` reads route month `yyyyMM`.
2. It computes month start/end dates.
3. It calls `getRecordCountByDateLabelRange(startDate, endDate)`.
4. Backend validates dates and returns counts from `RecordService.getRecordCountByDateLabelRange`.
5. FullCalendar renders day cells through `CalendarDay.vue`.

Inputs:

- Start and end date in `yyyy-MM-dd`.

Outputs:

- Daily record count DTOs.

## Portfolio Chart Flow

1. `PortfolioPage.vue` requests records with label `INVESTMENT_REC`.
2. It validates each record metadata with `validateTrade`.
3. It groups valid trades by symbol using `recordGroupBySymbolAndSort`.
4. It asks Vuex `portfolio/updateStockHistoryData` for stock daily history by symbol.
5. `PortfolioService.getStockDailyHistoryData` calls backend `POST /api/stock-data/get-daily-history`.
6. Backend reads persisted `StockDailyHistory` rows and returns valid histories plus invalid symbols.
7. Frontend converts history list to a symbol map.
8. `enrichAccumulativeTradeData` computes daily arrays per symbol:
   - `shares`
   - `cashFlow`
   - `cash`
   - `averageCostPerShare`
   - `totalCost`
   - `realizedPnL`
   - `unrealizedPnL`
   - `totalStockValue`
   - `totalPortfolio`
9. `PortfolioPage.vue` builds ECharts options for portfolio and metric charts.
10. Symbols with latest `shares.at(-1) === 0` are excluded from charts except `realizedPnL`.

Inputs:

- Investment record metadata and stock OHLCV history.

Outputs:

- ECharts option objects for total portfolio, allocation, and metric charts.

## Stock History Refresh Flow

1. Admin calls backend `POST /api/stock-data/update-daily-history-db`.
2. Backend `StockDataController` validates admin.
3. `MarketPulseApiService.getStockDailyHistory` calls Market Pulse `/stock-daily-history`.
4. Market Pulse uses yfinance/data source functions to return JSON history.
5. Backend converts API rows to `StockDailyHistory`.
6. `StockDailyHistoryService.updateStockDailyHistoryDatabase` upserts rows by unique symbol/date.

Inputs:

- Symbol list.

Outputs:

- Updated backend `stock_daily_history` table.

## Admin Job Management Flow

1. Backend startup runs `BuiltInJobSeeder`.
2. Seeder inserts or resets built-in rows in `scheduled_job_config` for existing long-term jobs.
3. Admin calls `GET /api/admin-tools/jobs/dashboard` or `/jobs/configs` to view jobs and latest status.
4. Admin updates enabled/schedule/retry fields through `PUT /api/admin-tools/jobs/configs/{id}`.
5. Admin manually triggers a job through `POST /api/admin-tools/jobs/configs/{id}/trigger`.
6. Backend creates a `job_execution` row with `QUEUED`.
7. `JobExecutionService` marks the row `RUNNING`, dispatches the matching `JobHandler`, then records the final status and structured details.
8. Stale `QUEUED`/`RUNNING` rows older than `max_runtime_seconds` are marked `TIMEOUT` during polling so future runs are not blocked.
9. When retry is enabled, retry executions are inserted as `QUEUED` before the failed attempt is marked `RETRYING`.
10. Failure email is sent after retries are exhausted, except for status-check jobs.
11. Admin views final status/details from job history in the dashboard.

Notes:

- Only admin users can access job config, trigger, and execution APIs.
- Manual triggers use saved job configuration only; trigger-time parameter overrides are not implemented yet.
- The DB scheduler poller is enabled by default and replaces old hardcoded `@Scheduled` cron methods.
- Qdrant record upsert/delete jobs are internal async consistency jobs and are hidden from the dashboard table. The visible Qdrant jobs are consistency check and manual datafix.

## Qdrant Consistency And Datafix Flow

1. Consistency check loads backend record ids and Market Pulse `/qdrant/record-ids`.
2. Missing ids are backend records not present in Qdrant. Stale ids are Qdrant vectors whose records no longer exist.
3. The manual `QDRANT_DATAFIX` job upserts missing backend records through `QdrantEmbeddingService.upsertRecordSync`.
4. The same datafix job deletes stale Qdrant vectors through `deleteRecordIfExistsSync`.
5. Per-record failures are recorded in job details while the job continues with other ids.

Output:

- All existing backend records should have Qdrant vectors.
- Deleted/nonexisting backend records should not remain in Qdrant.

## Scheduled Stock Data Jobs Flow

1. `BuiltInJobSeeder` seeds stock option/data update schedules only on weekdays:
   - `10:05`: option/stock data update task.
   - `13:30`: option/stock data update task.
   - `16:30`: option/stock data update task plus backend daily-history refresh.
   - `21:00`: option/stock data update task plus backend daily-history refresh.
2. `JobSchedulerService` queues due `scheduled_job_config` rows.
3. Each handler delegates to existing service logic such as `CronService` and returns a `JobResult` with summary/details.
4. When multiple jobs run in the same schedule, `sendStockJobEmail` sends one combined email containing each job name and result/detail.
5. If a job has no detail string, the email falls back to the job name.

Outputs:

- Updated stock/option resources and/or backend daily-history rows.
- One email per schedule fire, even when multiple stock jobs run together.

## Market News Summary Flow

1. `BuiltInJobSeeder` seeds the `MARKET_NEWS_SUMMARY_RECORD` job to run at `0 30 21 * * MON-FRI`.
2. Manual admin trigger on `/tools/admin` calls `POST /api/admin-tools/jobs/configs/{id}/trigger` for the `MARKET_NEWS_SUMMARY_RECORD` config.
3. Backend calls `MarketPulseApiService.getTrackedStockNewsSummary()`, passing the backend-owned stock-news prompt and `BuiltInLlmTokenLimits.MARKET_NEWS_SUMMARY_MAX_TOKENS`.
4. Market Pulse `/news/stock-summary` reads tracked news symbols from `market_pulse/resources/symbols/news_symbols.txt` when no symbols are provided.
5. Market Pulse calls `yf.Ticker(symbol).news`, normalizes articles, bounds article text with `NEWS_LLM_*` env settings, and passes the backend prompt/token value through to the configured LLM.
6. Market Pulse cleans model output by removing reasoning blocks, markdown fences, summary prefixes, ellipses, and unfinished trailing fragments. If the LLM fails, returns endpoint-error content, or produces no complete usable sentence, Market Pulse falls back to article title/summary text.
7. Backend sorts all symbol summaries alphabetically.
8. Backend creates public records in chunks controlled by `CronService.MARKET_NEWS_SUMMARY_SYMBOLS_PER_RECORD` to avoid overly long records.
9. Each record title includes the date or manual timestamp plus the chunk’s symbols.
10. Each record gets labels `MARKET_NEWS_SUMMARY`, `MARKET_PULSE`, current date, and uppercase symbol labels.
11. Each created record queues a durable `QDRANT_RECORD_UPSERT` execution.

Inputs:

- Default tracked stock symbols and yfinance news.

Outputs:

- Public market-news summary records grouped by the backend chunk-size constant.

## Option History Flow

1. `OptionHistoryPage.vue` calls `getOptionSymbols`.
2. Backend `OptionDataController.getOptionSymbols` proxies to Market Pulse `/options/symbols`.
3. User selects symbol; frontend calls `getOptionExpiries(symbol)`.
4. Backend proxies to Market Pulse `/options/expiry-dates`.
5. User selects option type/expiry; frontend calls `getOptionHistory(symbol, expiry, optionType)`.
6. Backend normalizes input and proxies to Market Pulse `/options/history`.
7. Market Pulse `OptionParquetReader` reads parquet data and returns strike histories.
8. Frontend caches history by `symbol|expiry|optionType` in Vuex and renders charts/lists.

Inputs:

- Symbol, expiry, option type.

Outputs:

- Option strike/history data for charting.

## Option Return Flow

1. `OptionReturn.vue` renders `OptionPositionList`.
2. User creates option positions with symbol, expiry, strike/type, quantity, and pricing assumptions.
3. Components may load symbols/expiries through `OptionHistoryService`.
4. `OptionReturnChart.vue` computes return/payoff series client-side and renders ECharts output.

Inputs:

- Option position rows and price range/assumptions.

Outputs:

- Payoff/return chart series.

## Deployment Flow

1. `docker-compose.yml` starts Qdrant.
2. Market Pulse starts with mounted resources and waits on Qdrant service start.
3. Market Pulse healthcheck calls `http://localhost:8000/health`.
4. Backend starts after Market Pulse is healthy.
5. Backend reads `.env` values for database, uploads/log paths, Market Pulse URL, mail, frontend URLs, and Qdrant collection.
6. Frontend static assets may be served by backend from `src/main/resources/static`, or run separately through Vue CLI during development.

Persistent data:

- MySQL database for backend entities.
- Backend upload directory for record files.
- Backend log directory.
- Market Pulse resources directory.
- Qdrant storage volume.
