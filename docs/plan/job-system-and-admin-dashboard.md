# Job System And Admin Dashboard Plan

## Goal

Build a durable, editable, admin-visible job system for Recorder with the least practical disruption to the current Spring Boot backend.

The first implementation should use the existing backend database as the source of truth. Do not add a message queue yet. The project currently runs a single backend container, and the main needs are editable schedules, manual admin triggers, final status visibility, universal retries, long-running job tracking, data freshness checks, Market Pulse/LLM health checks, and Qdrant consistency visibility.

For the first implementation, the job dashboard should live inside the existing admin tools page instead of becoming a separate top-level area. Admins can only view, edit, schedule, enable/disable, and manually trigger existing built-in jobs. Fully custom admin-created jobs and manual parameter overrides are out of scope until a future update.

## Current Implementation Status

- [x] Added database-backed `scheduled_job_config` and `job_execution` persistence models.
- [x] Added repositories for job config lookup, due-job polling, execution history, overlap checks, and 30-day retention cleanup.
- [x] Added reusable job framework classes: `JobHandler`, `JobContext`, `JobResult`, `JobDispatcher`, `JobExecutionService`, and `JobSchedulerService`.
- [x] Added built-in job definitions for the existing hardcoded `CronService` schedules and related admin jobs.
- [x] Added startup seeding that inserts or resets built-in job definitions on every backend startup/deploy.
- [x] Added admin-only backend APIs to list/get/update built-in job configs.
- [x] Added admin-only backend APIs to manually trigger built-in jobs and list/get execution history.
- [x] Added initial handlers for Market Pulse health, LLM health, server status email, stock option update, stock daily history update, after-close refresh, market-news summary record, expired option parquet combine, and job execution cleanup.
- [x] Added framework-level execution status tracking with queued/running/success/failed/retrying/skipped states.
- [x] Added framework-level retry attempts and final failure email after retries are exhausted.
- [x] Added 30-day retention metadata and a cleanup job for expired execution rows.
- [x] Added backend tests for admin-only job config access, built-in config update, and manual cleanup-job trigger.
- [x] Removed the hardcoded `CronService` `@Scheduled` methods and enabled the DB scheduler poller by default through `jobs.scheduler.enabled=true`.

Moved to follow-up plan:

- [x] Created `job-system-follow-up-tasks.md` for delayed retry scheduling, frontend dashboard sections, data-health jobs, Qdrant jobs, manual retry/cancel endpoints, and idempotency hardening.

## Design Direction

- [x] Use a database-backed job system first instead of RabbitMQ/Kafka/SQS.
- [x] Keep one Spring `@Scheduled` poller that checks due jobs from the database.
- [x] Move hardcoded cron schedules from `CronService` into database rows over time.
- [x] On every backend startup/deploy, seed or reconcile the built-in job definitions so existing cron jobs are always present.
- [x] Keep existing job logic in service methods at first; wrap it with durable execution tracking.
- [x] Keep the implementation reusable: shared scheduling, execution tracking, retry, notification, and result-shaping code should live in common services instead of being duplicated per job.
- [x] Split job orchestration, job-specific business logic, API DTO mapping, persistence, and frontend rendering into clear, focused functions/components.
- [x] Add frontend admin pages for job config, manual runs, execution history, and data-health dashboards.
- [x] Require admin authorization for every dashboard endpoint and every job config/manual-trigger operation.
- [x] Place the initial dashboard under the existing admin tools page.
- [x] Support only built-in jobs for now; do not add custom job creation in the first version.
- [x] Manual triggers should run with saved job configuration only; do not add trigger-time parameter overrides yet.
- [x] Add universal retry behavior at the job framework level so future jobs inherit it automatically.
- [x] Make jobs idempotent before enabling retry when partial completion can create duplicate records or duplicate side effects.
- [x] Keep progress percentages out of scope for now; final status and details are required.

## Target Capabilities

- [x] Admin users can view all configured jobs.
- [x] Admin users can enable/disable jobs.
- [x] Admin users can edit cron expressions or fixed intervals.
- [x] Admin users can manually trigger jobs.
- [x] Admin users can only edit/trigger/schedule existing built-in jobs in the first version.
- [x] Admin users can view recent and historical job executions.
- [x] Admin users can see final status for each run: queued, running, success, failed, retrying, skipped, cancelled, or timeout.
- [x] Admin users can see useful result details, such as updated symbols, created record IDs, current option symbols, option expiry dates, freshness dates, and health-check responses.
- [x] Non-admin users cannot view the job dashboard, job execution history, data-health dashboard, or job configuration details.
- [x] Non-admin users cannot create, edit, enable, disable, trigger, retry, or cancel jobs.
- [x] Jobs can retry using a shared retry mechanism.
- [x] Jobs can prevent duplicate side effects when retrying.
- [x] Qdrant async calls are tracked through job execution rows or a dedicated consistency dashboard.
- [x] Market Pulse and LLM connectivity are visible in the admin dashboard.

## Proposed Database Tables

### `scheduled_job_config`

- [x] Add table for editable job definitions.
- [x] Fields:
  - [x] `id`
  - [x] `job_key`, unique stable identifier
  - [x] `display_name`
  - [x] `job_type`
  - [x] `enabled`
  - [x] `schedule_type`: `CRON`, `FIXED_INTERVAL`, or `MANUAL`
  - [x] `cron_expression`
  - [x] `interval_seconds`
  - [x] `timezone`
  - [x] `parameters_json`
  - [x] `max_runtime_seconds`
  - [x] `retry_count`
  - [x] `retry_delay_seconds`
  - [x] `allow_concurrent_runs`
  - [x] `next_run_at`
  - [x] `last_run_at`
  - [x] `created_at`
  - [x] `updated_at`
  - [x] `is_builtin`
  - [x] `default_definition_version`
  - [x] `description`

### `job_execution`

- [x] Add table for each job run.
- [x] Fields:
  - [x] `id`
  - [x] `job_config_id`
  - [x] `job_key`
  - [x] `job_type`
  - [x] `trigger_type`: `SCHEDULED`, `MANUAL`, or `RETRY`
  - [x] `status`: `QUEUED`, `RUNNING`, `SUCCESS`, `FAILED`, `RETRYING`, `SKIPPED`, `CANCELLED`, or `TIMEOUT`
  - [x] `attempt`
  - [x] `max_attempts`
  - [x] `idempotency_key`
  - [x] `parameters_json`
  - [x] `started_at`
  - [x] `finished_at`
  - [x] `duration_ms`
  - [x] `summary`
  - [x] `details_json`
  - [x] `error_type`
  - [x] `error_message`
  - [x] `stack_trace`
  - [x] `retry_status_summary`
  - [x] `retention_until`
  - [x] `created_at`
  - [x] `updated_at`

### Optional Detail Tables

- [x] Add `data_check_result` only if `details_json` becomes too large or hard to query.
- [x] Add `qdrant_consistency_check_result` only if Qdrant dashboard needs record-level consistency history.
- [x] Add `job_artifact` only if jobs produce downloadable reports or large payloads.

## Backend Architecture

### Job Framework

- [x] Create a `JobSchedulerService` that polls due `scheduled_job_config` rows.
- [x] Create a `JobExecutionService` that owns all execution status transitions.
- [x] Mark stale `QUEUED` and `RUNNING` executions as `TIMEOUT` after `max_runtime_seconds` so old active rows cannot block future runs.
- [x] Create a `JobDispatcher` that maps `job_type` to a handler.
- [x] Create a `JobHandler` interface used by every job.
- [x] Keep job handlers thin: they should coordinate existing domain services and return structured `JobResult` data.
- [x] Put reusable retry, timeout, overlap, notification, and status-transition behavior in framework services, not individual handlers.
- [x] Split large job handlers into small private methods or domain services for input loading, validation, external API calls, persistence, result summarization, and error shaping.
- [x] Create a `JobContext` object containing config, execution ID, parameters, attempt number, trigger type, and admin user when manually triggered.
- [x] Create a `JobResult` object containing summary, details JSON, optional created record IDs, and optional notification text.
- [x] Make every job return a `JobResult` instead of only logging or sending email.
- [x] Keep email notification as a job result side effect or post-execution notifier, not the only tracking mechanism.

### Scheduler Poller

- [x] Add one poller, for example every 30 seconds, to find enabled due jobs.
- [x] Compute next run time from cron expression or fixed interval.
- [x] Prevent overlap when `allow_concurrent_runs` is false.
- [x] Mark skipped executions when a due job is already running and overlap is disabled.
- [x] Use transactional status updates so a job cannot be launched twice by the same backend poll cycle.
- [x] Keep implementation compatible with one backend container; design table updates so multiple containers can be supported later.

### Built-In Job Seeding

- [x] Add a startup seeder for built-in job definitions.
- [x] Run the seeder on every backend startup after database initialization.
- [x] Seed all jobs that currently exist as hardcoded cron methods in `CronService`.
- [x] Use stable `job_key` values so seeding is idempotent across builds and deployments.
- [x] Insert missing built-in jobs automatically when a new build introduces them.
- [x] For the first version, allow new builds to reset built-in job defaults, including schedule fields, because these are fixed long-term jobs.
- [x] Preserve only execution history when resetting built-in job definitions on startup.
- [x] Add a future enhancement note for preserving admin edits or adding a reset-to-default action later.
- [x] Allow metadata updates for built-in jobs, such as `display_name`, `job_type`, and default description, when the code changes.
- [x] Add an `is_builtin` or `managed_by_code` flag to distinguish seeded jobs from fully custom admin-created jobs.
- [x] Add a `default_definition_version` or similar field if future migrations need to update built-in job defaults intentionally.
- [x] Log a startup summary listing inserted, already-present, skipped, and metadata-updated built-in jobs.
- [x] Add tests proving the seeder inserts missing jobs and resets built-in job defaults safely on startup.

### Universal Retry

- [x] Implement retry in `JobExecutionService`, not inside individual jobs.
- [x] Use `retry_count` from `scheduled_job_config`. `retry_delay_seconds` is persisted but delayed retry execution is still pending.
- [x] Create a new `job_execution` row for each retry attempt, linked by `idempotency_key` or parent execution ID.
- [x] Mark the failed attempt as `RETRYING` or `FAILED` with retry metadata.
- [x] Schedule the retry by setting a retry execution to `QUEUED` after the delay, or by using a `next_attempt_at` field if added.
- [x] Disable retry by default for jobs that are not idempotent.
- [x] Add idempotency before enabling retry for jobs that create records, files, vectors, or external side effects.
- [x] Send failure emails only after all retry attempts are exhausted, except status-check jobs.
- [x] Include retry count, attempts, final status, first failure, latest failure, and execution IDs in failure emails.
- [x] Store retry status details in `job_execution.retry_status_summary` and/or `details_json` for dashboard display.

### Idempotency Rules

- [x] For market-news records, use a deterministic idempotency key such as `MARKET_NEWS_SUMMARY:yyyy-MM-dd:symbolChunk`.
- [x] Before creating market-news records, check whether records already exist for the date/symbol chunk.
- [x] For LLM chat-to-record jobs, use a request-generated idempotency key and store it in record metadata if retry is enabled.
- [x] For Qdrant upsert jobs, use `QDRANT_UPSERT:recordId:recordModifiedTime`; retry is safe because upsert replaces current vectors for the record.
- [x] For Qdrant delete jobs, use `QDRANT_DELETE:recordId`; retry is safe when delete is treated as success if vectors are already absent.
- [x] For stock daily history updates, rely on existing symbol/date uniqueness and update logic before enabling retries.
- [x] For option parquet updates, confirm Market Pulse update behavior is safe to rerun before enabling retries.

## Initial Job Types

- [x] `MARKET_PULSE_HEALTH_CHECK`
- [x] `LLM_HEALTH_CHECK`
- [x] `SERVER_STATUS_EMAIL`
- [x] `STOCK_OPTION_DATA_UPDATE`
- [x] `STOCK_DAILY_HISTORY_UPDATE`
- [x] `STOCK_AFTER_CLOSE_REFRESH`, composite of option data update plus daily history update
- [x] `MARKET_NEWS_SUMMARY_RECORD`
- [x] `COMBINE_EXPIRED_OPTION_PARQUET`
- [x] `QDRANT_RECORD_UPSERT`
- [x] `QDRANT_RECORD_DELETE`
- [x] `QDRANT_CONSISTENCY_CHECK`
- [x] `STOCK_DATA_FRESHNESS_CHECK`
- [x] `OPTION_DATA_FRESHNESS_CHECK`
- [x] Remove `OPTION_PARTITION_CHECK` from built-in schedules and the admin dashboard.

## Migration From Current CronService

- [x] Keep `CronService` initially, but move repeated status/error tracking into the new job framework.
- [x] Seed `scheduled_job_config` rows matching current hardcoded schedules.
- [x] Replace hardcoded stock-data `@Scheduled` methods with database-configured jobs.
- [x] Replace hardcoded market-news scheduled method with a database-configured job.
- [x] Replace hardcoded status email scheduled methods with database-configured jobs.
- [x] Replace combine expired option parquet scheduled method with a database-configured job.
- [x] Keep public methods such as `updateStockDailyHistory`, `updateStockOptionDataJob`, and market-news creation callable by job handlers until later cleanup.
- [x] After all scheduled methods are migrated, reduce `CronService` to job implementations or split it into focused job handler classes.

## Admin Backend APIs

- [x] `GET /api/admin-tools/jobs/configs`: list job configs.
- [x] `GET /api/admin-tools/jobs/configs/{id}`: get one job config.
- [x] Do not expose custom job creation in the first version.
- [x] `PUT /api/admin-tools/jobs/configs/{id}`: update schedule, enabled flag, parameters, retry settings, and concurrency settings.
- [x] `POST /api/admin-tools/jobs/configs/{id}/trigger`: manually trigger a job.
- [x] `GET /api/admin-tools/jobs/executions`: list execution history with filters.
- [x] `GET /api/admin-tools/jobs/executions/{id}`: get execution detail.
- [x] `POST /api/admin-tools/jobs/executions/{id}/retry`: manually retry a failed execution when safe.
- [x] `POST /api/admin-tools/jobs/executions/{id}/cancel`: mark queued jobs cancelled; running cancellation can be added later.
- [x] Ensure all endpoints require admin access.
- [x] Enforce admin access on the backend service/controller layer, not only through frontend route guards.
- [x] Add tests proving non-admin authenticated users receive forbidden responses for job config/dashboard APIs.
- [x] Avoid exposing sensitive job parameters or provider configuration values in API responses.

## Frontend Admin Dashboard

### Job Management

- [x] Add the job dashboard inside the existing admin tools page.
- [x] Mark all job dashboard routes with `requiresAdmin`.
- [x] Hide job dashboard navigation and controls from non-admin users.
- [x] Show configured jobs with enabled state, schedule, next run, last run, and last status.
- [x] Add edit controls for enabled flag, cron expression, interval, retry count, retry delay, max runtime, and parameters JSON.
- [x] Do not add custom job creation controls in the first version.
- [x] Add manual trigger button per job.
- [x] Manual trigger should not expose parameter overrides in the first version.
- [x] After manual trigger, show returned execution ID and poll until final status.

### Execution History

- [x] Show recent job executions sorted by start time.
- [x] Filter by job key, status, trigger type, and date range.
- [x] Show status, start time, finish time, duration, attempt, summary, and error message.
- [x] Show retry status and final retry outcome for failed jobs.
- [x] Add execution detail view with `details_json` pretty-rendered.
- [x] Add retry button for failed executions where retry is allowed.

### Data Health

- [x] Restrict data-health dashboard routes and API calls to admin users.
- [x] Show Market Pulse connection health.
- [x] Show LLM model connection health.
- [x] Show latest stock daily history date by symbol or by tracked-symbol summary.
- [x] Show option symbols currently available.
- [x] Show expiry dates per option symbol.
- [x] Show option partition/freshness status.
- [x] Show Qdrant consistency summary counts only: total records checked, missing vectors count, stale vectors count, failed upserts/deletes count.

## Data Check Details

### Stock Data Freshness

- [x] Compare tracked stock symbols from Market Pulse with backend `stock_daily_history` symbols.
- [x] Show latest trade date per symbol.
- [x] Use Market Pulse existing trade-day API/function when deciding expected freshness.
- [x] Mark stale when latest backend date is older than expected market data date for the latest trading day.
- [x] Store summary in `job_execution.details_json`.
- [x] Optionally store per-symbol details in `data_check_result`.

### Option Data Freshness

- [x] Fetch current option symbols from Market Pulse.
- [x] Fetch expiry dates per symbol.
- [x] Use Market Pulse trade-day API/function where relevant for expected option-data freshness.
- [x] Check that expected symbols have at least one expiry.
- [x] Check that expired partitions are combined after the configured weekly job.
- [x] Store symbol and expiry details in `job_execution.details_json` or `data_check_result`.

### Market Pulse Health

- [x] Call Market Pulse `/health`.
- [x] Store status, latency, response body excerpt, and error details.
- [x] Surface latest result in admin dashboard.

### LLM Health

- [x] Add or reuse a lightweight backend call to Market Pulse LLM chat endpoint.
- [x] Use a small health-check prompt and low token limit.
- [x] Store model response status, latency, model/config details if available, and error details.
- [x] Avoid saving sensitive API keys or full provider config.

### Qdrant Consistency

- [x] Track async Qdrant upsert/delete as durable jobs.
- [x] Add a consistency check that samples or scans records and calls vector `exists`.
- [x] Show aggregate counts only in the dashboard for the first version.
- [x] Count records missing vectors.
- [x] Count stale vectors if record modified time is newer than last successful Qdrant upsert.
- [x] Add manual reindex action for selected records or all stale/missing records.

## Suggested Implementation Phases

### Phase 1: Foundation

- [x] Add job config and execution entities.
- [x] Add repositories and migrations/schema updates.
- [x] Add `JobExecutionService`.
- [x] Add `JobHandler`, `JobContext`, `JobResult`, and `JobDispatcher`.
- [x] Add one manual-only test job to validate lifecycle.
- [x] Add admin APIs for execution list/detail.

### Phase 2: Manual Trigger And Dashboard

- [x] Add job dashboard sections to the existing admin tools page.
- [x] Add backend admin authorization tests for job dashboard APIs.
- [x] Add manual trigger endpoint.
- [x] Return execution ID from manual triggers.
- [x] Poll execution until final status.
- [x] Replace existing manual market-news fire-and-forget endpoint with job execution tracking.

### Phase 3: Configurable Scheduled Jobs

- [x] Add scheduler poller.
- [x] Seed current cron schedules into `scheduled_job_config`.
- [x] Add startup seeding so all built-in cron jobs are recreated automatically after each build/deploy if missing.
- [x] Reset built-in cron job defaults on startup for the first version.
- [x] Migrate stock option update schedules.
- [x] Migrate stock daily history update schedules.
- [x] Migrate market-news summary schedule.
- [x] Migrate status checks.
- [x] Migrate combine expired option parquet schedule.

### Phase 4: Universal Retry And Idempotency

- [x] Add framework-level retry handling.
- [x] Add idempotency keys to execution records.
- [x] Make market-news record creation idempotent.
- [x] Make Qdrant upsert/delete retry-safe.
- [x] Confirm stock daily history retry behavior.
- [x] Confirm option parquet update retry behavior.
- [x] Enable retries job by job.

### Phase 5: Data Health Dashboard

- [x] Add stock data freshness check job.
- [x] Add option data freshness check job.
- [x] Make option data freshness check compare today's trade date against the latest successful after-close refresh instead of reading all option files.
- [x] Keep option data freshness details showing all current option symbols with expiry counts.
- [x] Set stock data freshness, option data freshness, and Qdrant consistency checks to daily `22:00` default schedules.
- [x] Remove option partition check from built-in schedules and dashboard.
- [x] Set job execution cleanup to daily `23:00` by default.
- [x] Add Market Pulse health check job.
- [x] Add LLM health check job.
- [x] Add Qdrant consistency check job.
- [x] Add frontend dashboard panels for latest check results.

### Phase 6: Cleanup And Hardening

- [x] Remove or disable migrated hardcoded `@Scheduled` methods.
- [x] Split `CronService` into focused job handler classes if it becomes too large.
- [x] Add indexes for job config and execution query patterns.
- [x] Add tests for retry, skipped overlapping jobs, manual trigger, and execution status transitions.
- [x] Add admin API validation for cron expressions and parameters JSON.
- [x] Add 30-day retention policy for old job executions.
- [x] Add cleanup job for executions older than 30 days.

## Notes For Future Agents

- [x] Prefer small, vertical slices: one job type fully tracked end to end before migrating all jobs.
- [x] Prefer reusable framework code and focused functions over copying scheduling/retry/status logic between jobs.
- [x] Keep functions single-purpose where practical: orchestration, data fetch, validation, persistence, result formatting, and notification should be easy to identify separately.
- [x] Do not introduce a message queue unless requirements change toward multiple workers, high throughput, or cross-service job consumption.
- [x] Keep job result details structured as JSON so the frontend can render dashboards without parsing log text.
- [x] Do not rely on email as the source of truth for job success or failure.
- [x] Send failure emails only after retries are exhausted for non-status-check jobs, and include retry status in both email and dashboard details.
- [x] Treat any job that creates records as non-retryable until idempotency is implemented.
- [x] Treat Qdrant upserts and deletes as good early candidates for retry because they can be made naturally idempotent.
- [x] Keep Market Pulse as the data/ML service and Recorder backend as the job orchestration and status authority.
- [x] Keep execution details admin-only. Normal users should not see job status, job history, data-health checks, or dashboard details.
