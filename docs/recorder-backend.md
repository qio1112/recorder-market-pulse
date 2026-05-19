# Recorder Backend

## Module Summary

`recorder-backend` is a Java 17 Spring Boot service. It exposes authenticated REST APIs, stores application data through Spring Data JPA, serves uploaded files, schedules alerts, sends email, persists stock history, and proxies market/option/vector/LLM operations to Market Pulse.

## Frameworks And Libraries

- Spring Boot 3.3.3: web application runtime.
- Spring Security: JWT-protected stateless API.
- Spring Data JPA: entity/repository persistence layer.
- Spring Mail: user creation, alerts, and password reset emails.
- JJWT: token generation and parsing.
- MySQL Connector/J: runtime database driver.
- Apache Commons Lang: string helpers.
- RestTemplate: calls to Market Pulse.

## Package Responsibilities

- `com.yipeng.recorder.config`: Spring beans, security, CORS, startup initialization.
- `controller`: REST endpoint layer. Validates request context, loads authenticated user, delegates business logic.
- `service`: business logic, file handling coordination, auth helpers, scheduled work, Market Pulse/Qdrant integration.
- `model`: JPA entities and converters.
- `repository`: Spring Data repositories and custom query entry points.
- `request`: API request DTOs.
- `response`: API response DTOs and adapters for Market Pulse responses.
- `utils`: enums and utility classes.
- `exception`: custom exceptions and global API error handling.

## Controllers

### `AuthenticationController`

Base path: `/api/auth`.

- `POST /authenticate`
  - Input: `AuthenticationRequest { username, password }`.
  - Output: `{ token }`.
  - Flow: Spring `AuthenticationManager` validates credentials; `JwtUtil` creates token.
- `POST /signup`
  - Input: `SignUpRequest { username, email, password }`.
  - Output: text confirmation.
  - Flow: validates username uniqueness, hashes password with BCrypt, assigns `RoleType.USER`, saves `User`, sends email.
- `GET /user-info`
  - Input: JWT-authenticated user.
  - Output: `{ username, email, creationTime, isAdmin }`.
- `POST /change-password`
  - Input: `ChangePasswordRequest { currentPassword, newPassword }`.
  - Output: text confirmation.
  - Flow: delegates validation/update to `UserService`.
- `POST /forgot-password`
  - Input: `ForgotPasswordRequest { email }`.
  - Output: generic reset-link message.
  - Flow: delegates token creation and email to `PasswordResetService`.
- `POST /reset-password`
  - Input: `ResetPasswordRequest { token, newPassword }`.
  - Output: text confirmation.

### `RecordController`

Base path: `/api/records`.

- `POST /create-record`
  - Input: multipart form with JSON part `newRecordRequest` and optional `images`, `files`.
  - Output: created `Record`.
  - Flow: creates `Record`, optional `AlertSchedule`, writes uploaded files, creates labels, saves record, schedules alert, async upserts Qdrant vectors.
- `POST /update-record`
  - Input: multipart form with JSON part `updateRecordRequest` and optional new `images`, `files`.
  - Output: updated `Record`.
  - Flow: loads record, checks owner/admin modify permission, updates fields/metadata/labels/files/alert, deletes removed files, async upserts Qdrant vectors.
- `GET /record/{id}`
  - Input: record id.
  - Output: `Record`.
  - Flow: loads record and checks visibility.
- `GET /delete-record/{id}`
  - Input: record id.
  - Output: text confirmation.
  - Flow: checks modify permission, deletes record/files/alerts, async deletes Qdrant vectors if present.
- `POST /list-records`
  - Input: `ListRecordsRequest` filters: labels, excluded labels, title substring, date bounds, public flag, created-by-user flag, page/sort.
  - Output: Spring `Page<Record>`.
  - Flow: delegates filtered query to `RecordService.listRecordsCoreDataWithFilter`.
- `POST /record-count-by-date-label-in-range`
  - Input: `DateRangeRequest { startDate, endDate }`, format `yyyy-MM-dd`.
  - Output: list of `RecordDailyCountDto`.
  - Flow: validates date strings and returns counts for calendar UI.
- `POST /get-records-by-description`
  - Input: `QdrantQueryRequest { queryText/query_text, similarityThreshold, limit }`.
  - Output: `List<Record>`.
  - Flow: queries Qdrant via Market Pulse, filters by threshold, loads record IDs from database, re-applies visibility.
- `GET /record/{id}/related`
  - Input: record id.
  - Output: `List<RelatedRecordResponse>`, each item containing `{ record, score, chunks }`.
  - Flow: loads source record, checks visibility, queries Qdrant using `record.getEmbeddingString()`, excludes the source record, rechecks visibility, sorts by score, and returns up to 5 related records with default threshold `0.45`.

### `LlmController`

Base path: `/api/llm`.

- `POST /chat`
  - Input: `LlmChatRequest { messages, temperature?, max_tokens?, includeRelatedRecords? }`.
  - Output: `LlmChatResponse { reply }`.
  - Flow: admin-only proxy to Market Pulse `/llm/chat`. When `includeRelatedRecords` is true, the backend enriches the chat with relevant Qdrant chunks before proxying.
- `POST /record-labels`
  - Input: `GenerateRecordLabelsRequest { title, content, maxLabels }`.
  - Output: `GenerateRecordLabelsResponse { labels }`.
  - Flow: admin-only, uses `LlmRecordService.generateLabels` to request short, normalized record labels from the LLM. Parsing is tolerant of reasoning blocks, markdown, prose, quoted strings, and generic model output; if labels are still empty, backend derives fallback labels from record text.
- `POST /chat-record`
  - Input: `SaveLlmChatRecordRequest { messages, public }`.
  - Output: `SaveLlmChatRecordResponse { status, message }` with HTTP `202 Accepted`.
  - Flow: admin-only, starts `LlmRecordService.createChatRecordAsync(...)` and returns immediately. The async job summarizes the current chat, asks the LLM for a one-line title, generates up to 5 labels, creates a record, and upserts it to Qdrant.

### `AdminToolsController`

Base path: `/api/admin-tools`.

- `POST /market-news-summary-record`
  - Output: `{ status: "started", message }`.
  - Flow: admin-only, starts `CronService.createManualMarketNewsSummaryRecordAsync()` and returns immediately. The yfinance/LLM work continues in the background.

### `RecFileController`

Base path: `/api/recfile`.

- `GET /{fileID}`
  - Input: file id.
  - Output: binary file resource with probed content type.
  - Flow: loads `RecFile`, checks visibility using record/file ownership and public status, streams file from disk.

### `LabelController`

Base path: `/api/labels`.

- `GET /all-labels`: returns all `Label` records.
- `GET /label-exists/{label}`: returns boolean existence.

### `StockDataController`

Base path: `/api`.

- `POST /stock-data/get-daily-history`
  - Input: `StockHistoryRequest { symbols }`.
  - Output: `StockDailyHistoryFullResponse { InvalidSymbols, Data }`.
  - Flow: admin-only, normalizes symbols, reads persisted `StockDailyHistory` from backend database.
- `POST /stock-data/update-daily-history-db`
  - Input: `StockHistoryRequest { symbols }`.
  - Output: text summary.
  - Flow: admin-only, calls Market Pulse `/stock-daily-history`, converts response, upserts database rows.
- `GET /stock-data/update_stock_option_data`
  - Input: no body.
  - Output: Market Pulse task output string.
  - Flow: admin-only, invokes Market Pulse `/update-stock-data`.
- `GET /stock-data/update_stock_data/help`
  - Output: accepted Market Pulse stock task JSON schema and behavior summary.
- `GET /stock-data/news-summary`
  - Output: `StockNewsSummaryResponse`.
  - Flow: admin-only manual proxy to Market Pulse `/news/stock-summary`.

### `OptionDataController`

Base path: `/api/option-data`.

- `GET /symbols`
  - Output: `OptionSymbolsResponse`.
  - Flow: authenticated user, calls Market Pulse `/options/symbols`.
- `POST /expiries`
  - Input: `OptionExpiryDatesRequest { symbol }`.
  - Output: `OptionExpiryDatesResponse`.
  - Flow: normalizes symbol and calls Market Pulse.
- `POST /history`
  - Input: `OptionHistoryRequest { symbol, expiry, optionType }`.
  - Output: `OptionHistoryResponse`.
  - Flow: normalizes symbol/type and calls Market Pulse.
- `GET /combine-expired-parquet`
  - Output: `OptionParquetCombineResponse`.
  - Flow: admin-only, calls Market Pulse parquet combine task.

### `FrontendRouteController`

Serves the SPA fallback so frontend hash/history routes can resolve when static assets are hosted by the backend.

## Main Services

### `RecordService`

Main functions:

- `createRecord(record, images, regularFiles, labelNames, user, alertSchedule, isPublic, metadata) -> Record`
  - Inputs: prepared entity, uploaded file entities, label names, owner, optional alert, metadata map.
  - Outputs: persisted `Record`.
  - Behavior: enriches investment labels from metadata, creates missing labels, saves file metadata, attaches labels/files/alert, schedules alert.
- `updateRecord(...) -> Record`
  - Inputs: existing record, file ids to delete, new files, labels, alert changes, metadata.
  - Outputs: updated `Record`.
  - Behavior: updates label set, deletes removed file metadata and binary files, updates alert scheduling.
- `listRecordsCoreDataWithFilter(...) -> Page<Record>`
  - Inputs: filters, pagination, sort key, authenticated user.
  - Outputs: paged records visible to the user.
  - Behavior: normalizes empty filters, converts date bounds to zoned datetimes, delegates to repository query.
- `deleteRecord(record) -> void`
  - Deletes DB record, cancels alerts, deletes uploaded files.
- `getRecordCountByDateLabelRange(startDate, endDate, user) -> List<RecordDailyCountDto>`
  - Provides calendar aggregation.

### `MarketPulseApiService`

Gateway to Market Pulse:

- `getMarketPulseServerStatus()`: calls `/health`.
- `runUpdateStockOptionDataApi(request)`: calls `/update-stock-data` with longer timeout.
- `getStockDailyHistory(symbols)`: calls `/stock-daily-history` and converts JSON rows to `StockDailyHistory`.
- `getTrackedSymbols(forOption)`: calls `/stock-symbols` or `/option-symbols`.
- `getOptionSymbols()`, `getOptionExpiryDates(symbol)`, `getOptionHistory(symbol, expiry, optionType)`, `combineExpiredOptionParquetFiles()`: option API proxy methods.
- `getTrackedStockNewsSummary()`: calls Market Pulse `/news/stock-summary` with a long read timeout.
- `chatWithLlm(request)`: calls Market Pulse `/llm/chat`.
  - Uses a 30-second read timeout because local model responses can exceed the default 10-second `RestTemplate` timeout.
- `formatSymbolList(symbols)`: trims, uppercases, removes blanks and duplicates.

### `LlmRecordService`

Record-oriented LLM helpers:

- `generateLabels(title, content, maxLabels) -> List<String>`
  - Prompts the LLM to return a JSON array of short key labels.
  - Uses a larger output budget for smaller/reasoning-heavy models and explicitly asks for no reasoning/prose/markdown.
  - Parses JSON arrays first, then quoted strings, comma/newline lists, and finally label-like tokens.
  - Normalizes labels to uppercase, replaces spaces/punctuation with `_`, removes generic labels/stop words, enforces max length 30, and caps by requested limit.
  - If model labels are empty or unusable, derives fallback labels from title/content.
- `enrichChatWithRelatedChunks(request, user) -> LlmChatRequest`
  - Used by admin LLM chat when `includeRelatedRecords` is true.
  - Extracts the latest user message and queries Qdrant through `QdrantEmbeddingService.querySimilarRecords`.
  - Requests 20 candidate chunks from Qdrant, then backend filters/ranks to 5 chunks.
  - Rechecks each candidate’s DB record visibility before using its chunk text.
  - Default recency policy prefers records modified in the last 365 days.
  - Older records are excluded unless the query looks historical (`old`, `history`, year patterns, etc.) or the Qdrant score is high (`>= 0.72`).
  - Injected context includes `Source: {title} (Record {id})`, created/modified dates, score, and `possibly outdated` for old records.
  - Prompt tells the LLM to use excerpts as background information, synthesize rather than repeat chunks, cite title+id instead of bare record IDs, and prefer newer records when sources conflict.
- `createChatRecord(messages, isPublic, user) -> Record`
  - Builds a transcript from non-system messages.
  - Summarizes the chat.
  - Generates a one-line short title, stripping reasoning/markdown/prose. If unusable, derives a title from the summary/transcript.
  - Generates up to 5 labels, preferring single words while allowing compact phrases. If unusable, derives fallback labels.
  - Creates the record and asynchronously upserts Qdrant vectors.
- `createChatRecordAsync(messages, isPublic, user) -> CompletableFuture<Void>`
  - Fire-and-forget wrapper used by `/api/llm/chat-record`.
  - Copies messages before leaving the request thread and logs success/failure.

### `QdrantEmbeddingService`

Gateway to Market Pulse `/qdrant` endpoints:

- `upsertRecordAsync(record, user)`: builds `QdrantUpsertRequest` from record id, user id, public flag, embedding text, labels, and collection.
- `upsertRecordSync(record, user)`: synchronous variant used by startup/bootstrap flows.
- `deleteRecordIfExistsAsync(record)`: checks vector existence and deletes if present.
- `querySimilarRecords(queryText, user, threshold, limit)`: returns `QdrantQueryResult` values from vector search.
  - `QdrantQueryResult.chunks` contains matched chunk text from Qdrant payloads. Related-record UI and LLM chat context should use chunks rather than loading full record content when possible.
- `recordExists(recordId)`: checks vector presence.

### Authentication And User Services

- `CustomUserDetailsService`: loads `User` by username for Spring Security.
- `CustomUserDetails`: adapts domain `User` and roles to Spring `UserDetails`.
- `JwtRequestFilter`: reads `Authorization` bearer token, validates it, and installs authentication into the security context.
- `UserService`: resolves the authenticated user, checks record/file visibility and modification permission, changes passwords.
- `PasswordResetService`: creates hashed reset tokens, sends reset email, validates token, updates password.
- `SendEmailService`: sends email messages through configured SMTP.

### Scheduling And Startup

- `ScheduleAlertService`: schedules one-time or recurring record alerts and cancels scheduled tasks by record id.
- `CronService`: scheduled background jobs for Market Pulse health/data refresh and notifications.
  - Stock option/data update schedules run weekdays only:
    - `0 5 10 * * MON-FRI`
    - `0 30 13 * * MON-FRI`
    - `0 30 16 * * MON-FRI`
    - `0 0 21 * * MON-FRI`
  - When a scheduled stock update runs multiple jobs together, such as option data plus daily history at 16:30/21:00, it sends one combined email with all job names/results.
  - Daily weekday market-news summary job runs at `0 0 21 * * MON-FRI`.
  - It calls Market Pulse news summaries for default tracked news symbols, sorts symbols alphabetically, and creates one public record per 5 symbols.
  - Each news record has labels `MARKET_NEWS_SUMMARY`, `MARKET_PULSE`, current date, and the chunk’s uppercase symbols.
  - Manual admin-tool runs use the same chunking/labeling behavior but include a timestamp in the title and run asynchronously.
- `StartupRunner`: seeds roles/labels/admin user, restores alert schedules, and can synchronize existing records into Qdrant.

## Persistence Model

- `User`: account with username, BCrypt password, email, roles, timestamps.
- `Role`: enum-backed role entity using `RoleType`.
- `Record`: title, content, owner, public flag, timestamps, labels, files, alert schedule, metadata JSON. Custom JSON getters expose core label/file fields and `createdBy`.
- `Label`: label name, type (`DATE` or `REGULAR`), creator.
- `RecFile`: file name, type (`IMAGE` or `REGULAR_FILE`), upload path, uploader, computed filesystem `Path`.
- `AlertSchedule`: alert type (`ONE_TIME` or `RECURRING`), time, weekdays, linked record.
- `PasswordResetToken`: hashed reset token, expiration/usage metadata, linked user.
- `StockDailyHistory`: symbol, trade date, OHLC, volume with unique `symbol + trade_date`.
- `StringMapJsonConverter`: converts `Map<String,String>` metadata to/from JSON text.

## API Data Shape Notes

- Records expose `labels` as objects with `labelName` and `type`.
- Records expose `recFiles` as objects with `fileID`, `filename`, and `fileType`.
- Investment records use metadata keys such as `symbol`, `price`, `shares`, `action`, and `date`; labels are enriched with `symbol` and `date` when `INVESTMENT_REC` is present.
- Stock history response uses `InvalidSymbols` and `Data`; each symbol history is converted by frontend into `{ [symbol]: history }`.
