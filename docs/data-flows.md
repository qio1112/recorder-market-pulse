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

## LLM Chat Related Chunk Context Flow

1. Admin sends a message from `/tools/llm-chat`.
2. Frontend `LlmService.sendLlmChat(messages, { includeRelatedRecords: true })` posts only `{ role, content }` message fields plus `includeRelatedRecords`.
3. Backend `LlmController.chat` validates admin access.
4. `LlmRecordService.enrichChatWithRelatedChunks` extracts the latest user message.
5. Backend queries Qdrant through `QdrantEmbeddingService.querySimilarRecords` with threshold `0.45` and candidate limit `20`.
6. Market Pulse `/qdrant/query` returns record-level hits with matched chunk text in `chunks`.
7. Backend loads each candidate record from MySQL and rechecks visibility.
8. Backend applies recency logic:
   - Prefer records modified within the last 365 days.
   - Exclude older records unless the user asks for historical/old records or Qdrant score is at least `0.72`.
   - Sort recent records ahead of old records, then by similarity score and modified time.
9. Backend keeps up to 5 chunks, max 1200 chars per chunk and about 7000 chars total related context.
10. Backend injects a system context message before the user-visible chat content.
11. The injected context names sources as `{title} (Record {id})`, includes created/modified dates, marks old records as `possibly outdated`, and tells the LLM to use excerpts as background rather than repeating them.
12. Backend forwards the enriched request to Market Pulse `/llm/chat`.

Inputs:

- Latest user chat message and authenticated user.
- Qdrant chunk text plus DB record visibility/date metadata.

Outputs:

- LLM reply that may cite source titles with record IDs, while using only relevant chunks rather than full record bodies.

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
9. Backend creates a private record with summary and transcript content.
10. `QdrantJobService.queueUpsert` queues durable Qdrant indexing for the new record.

Inputs:

- Current chat messages.

Outputs:

- Immediate job-start response to the frontend.
- Eventually, a new record with LLM/fallback title, summary, transcript, labels, and Qdrant vectors.

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

## Scheduled Stock Data Jobs Flow

1. `CronService` runs stock option/data update schedules only on weekdays:
   - `10:05`: option/stock data update task.
   - `13:30`: option/stock data update task.
   - `16:30`: option/stock data update task plus backend daily-history refresh.
   - `21:00`: option/stock data update task plus backend daily-history refresh.
2. Each job returns a `StockJobResult { name, detail }` to the schedule handler.
3. When multiple jobs run in the same schedule, `sendStockJobEmail` sends one combined email containing each job name and result/detail.
4. If a job has no detail string, the email falls back to the job name.

Outputs:

- Updated stock/option resources and/or backend daily-history rows.
- One email per schedule fire, even when multiple stock jobs run together.

## Market News Summary Flow

1. Scheduled job `CronService.runMarketNewsSummary2100Weekdays` runs at `0 0 21 * * MON-FRI`.
2. Manual admin trigger on `/tools/admin` calls `POST /api/admin-tools/market-news-summary-record`, which starts the same work asynchronously and returns immediately.
3. Backend calls `MarketPulseApiService.getTrackedStockNewsSummary()`.
4. Market Pulse `/news/stock-summary` reads tracked news symbols from `market_pulse/resources/symbols/news_symbols.txt` when no symbols are provided.
5. Market Pulse calls `yf.Ticker(symbol).news`, normalizes articles, bounds article text with `NEWS_LLM_*` env settings, and uses the configured LLM to produce one paragraph per symbol.
6. Market Pulse cleans model output by removing reasoning blocks, markdown fences, and summary prefixes. If the LLM fails, returns endpoint-error content, or produces no usable summary, Market Pulse falls back to article title/summary text.
7. Backend sorts all symbol summaries alphabetically.
8. Backend creates one public record per 5 symbols to avoid overly long records.
9. Each record title includes the date or manual timestamp plus the chunk’s symbols.
10. Each record gets labels `MARKET_NEWS_SUMMARY`, `MARKET_PULSE`, current date, and uppercase symbol labels.
11. Each created record is asynchronously upserted to Qdrant.

Inputs:

- Default tracked stock symbols and yfinance news.

Outputs:

- Public market-news summary records grouped by 5 symbols.

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

## Admin Job Management Flow

1. Backend startup runs `BuiltInJobSeeder`.
2. Seeder inserts or resets built-in rows in `scheduled_job_config` for existing long-term jobs.
3. Admin calls `GET /api/admin-tools/jobs/dashboard` or `/jobs/configs` to view jobs and latest status.
4. Admin can create custom schedules for registered handlers through `POST /api/admin-tools/jobs/configs`.
5. Admin can update enabled/schedule/retry fields through `PUT /api/admin-tools/jobs/configs/{id}`.
6. Admin can delete custom schedules through `DELETE /api/admin-tools/jobs/configs/{id}`; built-in jobs can only be disabled.
7. Admin can manually trigger a job through `POST /api/admin-tools/jobs/configs/{id}/trigger`.
8. Backend creates a `job_execution` row with `QUEUED`.
9. `JobExecutionService` marks the row `RUNNING`, dispatches the matching `JobHandler`, then records `SUCCESS`, `FAILED`, `RETRYING`, or `SKIPPED`.
10. Handler returns structured `JobResult` summary/details JSON for dashboard display.
11. If retries are exhausted, backend sends a failure email that includes retry status and execution IDs.
12. Admin calls `GET /api/admin-tools/jobs/executions` or `/jobs/executions/{id}` to view final status.

Notes:

- Only admin users can access job config, trigger, and execution APIs.
- Manual triggers use saved job configuration only; trigger-time parameter overrides are not implemented yet.
- The DB scheduler poller is enabled by default and replaces the old hardcoded `CronService` scheduled methods.
