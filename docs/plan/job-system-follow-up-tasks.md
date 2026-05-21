# Job System Follow-Up Tasks

This file tracks work intentionally moved out of the first DB-backed job-system migration. The initial plan in `job-system-and-admin-dashboard.md` now represents the completed foundation and cron migration.

## Scheduler And Retry Hardening

- [ ] Add delayed retry scheduling using `retry_delay_seconds`.
- [ ] Add `next_attempt_at` or an equivalent retry scheduling field if needed.
- [ ] Add manual retry endpoint: `POST /api/admin-tools/jobs/executions/{id}/retry`.
- [ ] Add cancel endpoint: `POST /api/admin-tools/jobs/executions/{id}/cancel`.
- [ ] Add stronger transactional claiming so multiple backend containers can safely poll the same due jobs later.
- [ ] Add validation for cron expressions, interval values, retry settings, and parameters JSON.
- [ ] Add tests for delayed retry, skipped overlap, manual retry, cancel, and status transitions.

## Idempotency

- [ ] Add idempotency keys to execution records in a deterministic way per job.
- [ ] Make market-news record creation idempotent by date and symbol chunk.
- [ ] Before creating market-news records, check whether records already exist for the date/symbol chunk.
- [ ] Add idempotency metadata for LLM chat-to-record jobs before enabling retries there.
- [ ] Confirm stock daily history retry behavior stays safe with existing symbol/date uniqueness and update logic.
- [ ] Confirm Market Pulse option parquet update behavior is safe to rerun before enabling retries.

## Qdrant Jobs

- [x] Add `QDRANT_RECORD_UPSERT` built-in job type and handler.
- [x] Add `QDRANT_RECORD_DELETE` built-in job type and handler.
- [x] Convert record create/update/delete Qdrant calls from direct `@Async` calls to durable job executions.
- [x] Use `QDRANT_UPSERT:recordId:recordModifiedTime` as the retry-safe upsert key.
- [x] Use `QDRANT_DELETE:recordId` as the retry-safe delete key.
- [x] Add `QDRANT_CONSISTENCY_CHECK` built-in job type and handler.
- [x] Show count-level Qdrant consistency status in the admin dashboard: checked count, missing count, stale count, failed upsert/delete count.
- [ ] Add manual reindex action for all stale/missing records.

## Data Health Jobs

- [x] Add `STOCK_DATA_FRESHNESS_CHECK` built-in job type and handler.
- [x] Use Market Pulse trade-day API/function when deciding expected stock data freshness.
- [x] Compare tracked stock symbols with backend `stock_daily_history`.
- [x] Store latest trade date and stale count in `job_execution.details_json`.
- [x] Add `OPTION_DATA_FRESHNESS_CHECK` built-in job type and handler.
- [x] Use latest successful after-close refresh as the option freshness reference while still recording current option symbols and expiry counts for the dashboard.
- [x] Remove option partition check from the built-in dashboard surface.
- [x] Keep data-health results in `job_execution.details_json`; `data_check_result` is still unnecessary.

## Frontend Admin Dashboard

- [x] Add job dashboard sections inside the existing admin tools page.
- [x] Mark all job dashboard routes with `requiresAdmin`.
- [x] Hide job dashboard navigation and controls from non-admin users.
- [x] Show configured jobs with enabled state, schedule, next run, and last status.
- [x] Add edit controls for enabled flag, cron expression, interval, retry count, retry delay, max runtime, and parameters JSON.
- [x] Add admin schedule creation and custom schedule deletion controls for registered job handlers.
- [x] Add manual trigger button per job.
- [x] Manual trigger should not expose parameter overrides yet.
- [x] Poll manual-trigger execution until final status.
- [x] Show recent job executions sorted by creation time.
- [ ] Filter execution history by trigger type and date range.
- [x] Filter execution history by job key and status.
- [x] Add execution detail view with pretty-rendered `details_json`.
- [x] Show retry status and final retry outcome for failed jobs.
- [x] Add data-health panels for Market Pulse, LLM, stock freshness, option freshness, and Qdrant counts.

## Cleanup

- [ ] Split `CronService` into focused job/domain services if it becomes too large.
- [x] Add indexes for any new execution-history dashboard query patterns.
- [ ] Consider moving built-in job defaults to a config/data class if the list grows significantly.
- [ ] Keep the message queue decision deferred until multiple workers, high throughput, or cross-service job consumption are required.
