# Recorder Frontend

## Module Summary

`recorder-frontend` is a Vue 3 SPA built by Vue CLI. It uses Vue Router for route control, Vuex for global state, Axios for backend APIs, ECharts for finance charts, and FullCalendar for the calendar view.

## Frameworks And Libraries

- Vue 3.2 and Vue CLI 5.
- Vue Router 4 with hash history.
- Vuex 4 modules for user, portfolio, records, and option history.
- Axios with an API base URL of `/api`.
- ECharts 5 and `vue-echarts` for line/pie charts.
- FullCalendar day grid for record counts by date.

## Entry And App Shell

- `src/main.js`
  - Creates the Vue app, registers router/store, globally registers `BaseButton`, mounts to `#app`.
- `src/App.vue`
  - Provides the app shell and header.
- `src/components/TheHeader.vue`
  - Shows navigation and account/logout controls based on user auth state.
- `src/components/ui/BaseButton.vue`
  - Shared styled button component.

## HTTP Layer

### `src/api/config.js`

- Exports `API_BASE_URL = '/api'`.

### `src/api/http.js`

- Creates an Axios instance with:
  - `baseURL: '/api'`
  - `timeout: 10000`
- Request interceptor:
  - Reads `store.getters['user/getJwtToken']`.
  - Adds `Authorization: Bearer <token>` when present.

### API Client Files

- `RecordService.js`
  - `getRecords(ListRecordRequest) -> Page<Record>`.
  - `getRecordDetail(recordID) -> Record`.
  - `getRecFile(fileID) -> Blob`.
  - `deleteRecord(recordID) -> string`.
  - `addNewRecord(AddRecordRequest) -> Record`: sends multipart `newRecordRequest`, `images`, and `files`.
  - `editRecord(EditRecordRequest) -> Record`: sends multipart `updateRecordRequest`, `images`, and `files`.
  - `getRecordCountByDateLabelRange(startDate, endDate) -> RecordDailyCountDto[]`.
  - `getTextQueryRecords(ListDescribedRecordRequest) -> Record[]`.
  - Request classes define frontend form state and `toApi()` transforms.
- `UserService.js`
  - `authenticate(username, password) -> token | false`.
  - `parseJwtInfo(token) -> { token, payload, exp, expDate, expired, username }`.
  - `isTokenExpired(token) -> boolean`.
  - `getUserInfo()`, `changePassword()`, `requestPasswordReset()`, `resetPassword()`.
- `PortfolioService.js`
  - `getStockDailyHistoryData(symbols) -> { data, invalidSymbols }`.
  - Converts backend `Data` list into a map with `convertListHistoryToMap`.
- `OptionHistoryService.js`
  - `getOptionSymbols() -> string[]`.
  - `getOptionExpiries(symbol) -> string[]`.
  - `getOptionHistory(symbol, expiry, optionType) -> OptionHistoryResponse`.
- `LlmService.js`
  - `sendLlmChat(messages, { includeRelatedRecords=false } = {}) -> { reply }`.
  - Sends only API-safe chat fields `{ role, content }`; UI-only fields such as timestamps are stripped before the request.
  - Uses a 60-second timeout for normal chat and sends `max_tokens: 5000`.
  - `generateRecordLabels({ title, content, maxLabels }) -> { labels }`.
  - `saveLlmChatAsRecord(messages, isPublic=false) -> { status, message }`.
  - Uses longer per-call timeouts for label generation/chat-record job start than the shared Axios default.
- `AdminToolsService.js`
  - `createMarketNewsSummaryRecord() -> { status, message }`.
  - Starts the backend manual market-news summary job and returns when the job is accepted.

## Router

`src/router/index.js` uses `createWebHashHistory`.

Routes:

- `/` redirects to `/records`.
- `/records`: record list.
- `/calendar` redirects to current month `/calendar/yyyyMM`.
- `/calendar/:month(\\d{6})`: calendar page.
- `/records/:recordID`: detail page.
- `/add-record`: create page.
- `/edit-record/:recordID`: edit page.
- `/tools/portfolio`: portfolio charts.
- `/tools/option-return`: option return calculator.
- `/tools/option-history`: option history charting.
- `/tools/llm-chat`: admin-only LLM chat console.
- `/tools/admin`: admin-only manual backend tools.
- `/account`: user account.
- `/login`, `/forgot-password`, `/reset-password`: unauthenticated account flows.
- `/:notFound(.*)`: not found page.

Navigation guard:

- Blocks `requiresAuth` routes when `user/isUserAuthenticated` is false.
- Loads `/api/auth/user-info` when an admin-only route needs the current admin flag.
- Blocks `requiresAdmin` routes for non-admin users.
- Redirects authenticated users away from `requiresUnauth` pages.

## Vuex Store

### `src/store/index.js`

Registers modules:

- `records`
- `user`
- `portfolio`
- `optionHistory`

### `store/user`

State:

- `isAuthenticated`
- `jwtToken`
- `username`
- `isAdmin`
- `userInfoLoaded`

Initialization:

- Reads `localStorage.token`.
- Parses JWT and treats expired/invalid token as unauthenticated.
- Clears LLM chat history when auth is invalid, on logout, failed login, or new login.

Actions:

- `authenticateUser({ username, password })`: calls `authenticate`, stores token, clears option-history selection/cache, commits login/logout.
- `logoutUser()`: clears token and option-history selection/cache.

### `store/portfolio`

State:

- `symbols`
- `invalidSymbols`
- `stockData`
- `portfolioData`

Actions:

- `updateStockHistoryData(symbols)`: calls `PortfolioService.getStockDailyHistoryData`, commits valid symbols, invalid symbols, and stock history map.
- `updatePortfolioData(data)`: stores derived portfolio data.

Getters:

- `getStockDailyHistoryData`
- `getInvalidSymbols`

### `store/optionHistory`

State:

- `historyByKey`: keyed by `symbol|expiry|optionType`.

Actions:

- `loadOptionHistory(payload)`: returns cached history if present, otherwise fetches backend option history.
- `clearCache()`: clears cached histories.

## Records UI

- `views/RecordsPage.vue`
  - Composes filters and list components.
  - Keeps selected filter state and list request shape.
- `components/records/RecordsFilter.vue`
  - Builds `ListRecordRequest` filters from user input.
  - Emits filter changes to page/list layer.
- `components/records/RecordsList.vue`
  - Fetches records through `getRecords` or semantic search through `getTextQueryRecords`.
  - Manages pagination and renders `RecordPreview`.
- `components/records/RecordPreview.vue`
  - Shows record summary, labels, attached images/files; fetches blobs through `getRecFile` when needed.
- `components/records/ImagePreview.vue`
  - Displays image blob URLs and cleans up object URLs.
- `views/RecordDetail.vue`
  - Fetches one record by route param.
  - Shows full content/files and handles delete navigation.
  - Long content is shown in a bounded scroll area.
  - Related records load asynchronously after the main detail view renders, with their own loading/error state.
- `views/AddNewRecord.vue`
  - Uses `RecordEditForm`; submits `AddRecordRequest`.
- `views/EditRecord.vue`
  - Loads record detail, seeds `RecordEditForm`; submits `EditRecordRequest`.
- `components/records/RecordEditForm.vue`
  - Shared create/edit form for title, content, public flag, labels, metadata, alerts, existing files, removed files, new uploads.
  - Investment metadata helper ensures trade metadata fields when `INVESTMENT_REC` is selected.
  - Shows an LLM `Generate Labels` button only when the LLM connection check succeeds.
  - Generated labels are merged into the form without duplicating existing labels.

## Admin And LLM Tools

- `views/AdminLlmChatPage.vue`
  - Admin-only page under Tools.
  - Checks LLM availability through `/api/llm/chat`; shows `No LLM connection` when unavailable.
  - Uses a viewport-height panel so the conversation fills most of the screen and scrolls internally.
  - Stores visible chat messages in `localStorage` under `recorder.llmChat.messages`.
  - Chat messages include `createdAt` timestamps for display. Older stored messages without timestamps still load.
  - Transient LLM failures/timeouts keep the visible and stored chat history; only auth failures clear history.
  - Real user chat sends set `includeRelatedRecords: true`, so backend can inject top related Qdrant chunks. Connection checks do not include related records.
  - The refresh icon starts a new chat by clearing on-screen messages and saved history.
  - `Add Chat As Record` sends the current visible chat to `/api/llm/chat-record`; backend accepts the job asynchronously and the page shows that creation started rather than waiting for completion.
- `views/AdminToolsPage.vue`
  - Admin-only page under Tools.
  - Shows job status panels for Market Pulse, LLM, stock freshness, option freshness, and Qdrant count consistency.
  - Groups schedules by job type so repeated built-in jobs, such as status email or stock/option updates at multiple times, appear as one job with multiple schedule chips.
  - Separates status-check jobs from data-update jobs. Data-update jobs include stock/option updates, after-close stock refreshes, market-news record generation, and expired option parquet combines.
  - Hides internal Qdrant record upsert/delete jobs from the dashboard job tables; only the Qdrant consistency check remains visible.
  - Each grouped job has one compact manual `Run` action. Triggered jobs show an immediate `STARTING` state with a spinner for at least one second before polling the real execution status.
  - Each grouped job has its own history dropdown backed by job-type execution history. Long history lists scroll, and selecting an execution shows admin-only details.
  - Status-check jobs include a `Run All` action that triggers every visible status check without changing data-update jobs.
  - Individual schedule chips open the schedule editor for that concrete existing `scheduled_job_config` row; the UI does not expose custom schedule creation yet.
- `components/TheHeader.vue`
  - Tools dropdown includes Portfolio, Option Return, Option History, admin-only LLM Chat, and admin-only Admin Tools.

## Calendar UI

- `views/CalendarPage.vue`
  - Uses FullCalendar day grid.
  - Reads month from route.
  - Calls `getRecordCountByDateLabelRange(startDate, endDate)`.
  - Renders custom day cells through `CalendarDay`.
- `components/calendar/CalendarDay.vue`
  - Displays per-day record counts and navigates to record filters for that date.

## Portfolio UI

- `views/PortfolioPage.vue`
  - Fetches investment records with label `INVESTMENT_REC`.
  - Validates trade metadata using `validateTrade`.
  - Groups/sorts trades by symbol with `recordGroupBySymbolAndSort`.
  - Loads stock history through Vuex portfolio store.
  - Enriches each symbol with daily shares, cash flow, cash, average cost, total cost, realized PnL, unrealized PnL, stock value, and total portfolio.
  - Builds ECharts options for total portfolio, allocation donut, and per-metric line charts.
  - Excludes symbols whose latest shares are zero from all charts except `realizedPnL`.
- `components/portfolio/DashboardItem.vue`
  - Shared panel wrapper for chart titles/subtitles/content.
- `utils/portfolioUtils.js`
  - `validateTrade(metaObj) -> boolean`: requires symbol, positive price/shares, `buy|sell`, and `yyyy-MM-dd`.
  - `recordGroupBySymbolAndSort(trades) -> { [symbol]: trades[] }`.
  - `enrichAccumulativeTradeData(tradesBySymbol, historyData) -> { [symbol]: enriched }`.
  - `enrichAccumulativeTradeDataForSymbol(trades, historyData) -> enriched`: computes daily position and PnL series.
  - `aggregateMetricAcrossSymbols(enrichedData, metric) -> { dates, values }`.
  - `convertListHistoryToMap(historyDataList) -> { [Symbol]: item }`.

Portfolio inputs:

- Records metadata: `{ symbol, price, shares, action, date }`.
- Stock history per symbol: `{ Symbol, Datetime, Open, High, Low, Close, Volume }`.

Portfolio outputs:

- Chart option objects stored in `chartOptions`.
- `donutOption` for latest allocation.
- `invalidSymbols` for backend history misses.

## Option Tools

- `views/OptionHistoryPage.vue`
  - Loads option symbols and expiries.
  - Maintains selected options in local storage.
  - Uses `OptionHistoryLineChart` and `SelectedOptionList`.
- `components/option-history/OptionHistoryLineChart.vue`
  - ECharts line chart for option strike/history data.
- `components/option-history/OptionSymbolExpiryPanel.vue`
  - Symbol and expiry selector.
- `components/option-history/SelectedOptionList.vue`
  - Displays selected option contracts and controls removal.
- `views/OptionReturn.vue`
  - Hosts option return calculator.
- `components/option-return/OptionPositionList.vue`
  - Manages option positions, loads available symbols/expiries, persists positions in local storage.
- `components/option-return/OptionPositionRow.vue`
  - Per-position editable inputs.
- `components/option-return/OptionReturnChart.vue`
  - Computes and charts option return/payoff curves.

## Account Pages

- `views/LoginPage.vue`: dispatches `user/authenticateUser`.
- `views/ForgotPasswordPage.vue`: calls password reset request API.
- `views/ResetPasswordPage.vue`: reads token from route/query and calls reset API.
- `views/UserAccountInfo.vue`: fetches account info and supports password change.
- `views/PageNotFound.vue`: fallback display.
