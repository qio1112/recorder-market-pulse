# Scheduling And Admin Operations

This file is the quick orientation point for all scheduled/background work in Recorder. Read this before changing cron-like jobs, record alerts, admin dashboard behavior, or Qdrant consistency work.

## Two Scheduling Systems

Recorder intentionally has two separate database-backed scheduling systems.

| System | Tables | Audience | Purpose |
| --- | --- | --- | --- |
| Admin jobs | `scheduled_job_config`, `job_execution` | Admin users only | System/data jobs, status checks, Market Pulse calls, Qdrant maintenance, cleanup |
| Record alerts | `alert_schedule`, `alert_execution` | All users, visibility filtered | User-created alert emails for individual records |

Do not merge these tables. Admin jobs are operational tooling; record alerts are user features tied to records and record ownership.

## Admin Job Framework

Core classes:

- `BuiltInJobSeeder`: seeds built-in jobs into `scheduled_job_config` on every backend startup.
- `JobSchedulerService`: polls DB schedules and queues due jobs.
- `JobExecutionService`: owns execution lifecycle, retry status, stale-running timeout handling, failure email, and retention.
- `JobDispatcher`: maps `JobType` to a registered `JobHandler`.
- `AdminToolsController`: admin-only REST API for the dashboard.
- `AdminToolsPage.vue`: dashboard UI under `/tools/admin`.

Important behavior:

- Built-in jobs are reset from code on startup so deploys always restore known long-term schedules.
- Admin users can edit/enable/disable and manually trigger existing jobs. The UI currently does not expose adding new schedules.
- Manual triggers use saved job config only; trigger-time parameter overrides are intentionally deferred.
- `QUEUED` or `RUNNING` executions older than `max_runtime_seconds` are marked `TIMEOUT` so container rebuilds or interrupted async tasks do not block future runs.
- Retry is framework-level. Retry status is stored in `job_execution`; failure email is sent only after retry attempts are exhausted.
- Status-check jobs do not send failure emails. They fail visibly in the dashboard only.
- Job history retention is 30 days. `JOB_EXECUTION_CLEANUP` runs at 23:00 America/New_York.

Dashboard grouping:

- Jobs are grouped by `jobType`, so multiple schedules for the same built-in job appear as one row with multiple schedule chips.
- Status checks are separate from data update jobs.
- Qdrant record upsert/delete jobs are internal and hidden from dashboard job tables. The visible Qdrant job is the count-level consistency check.

Key built-in job defaults:

- `STOCK_DATA_FRESHNESS_CHECK`: daily 22:00.
- `OPTION_DATA_FRESHNESS_CHECK`: daily 22:00. Uses latest successful `STOCK_AFTER_CLOSE_REFRESH` as freshness reference and still reports option symbols with expiry counts.
- `QDRANT_CONSISTENCY_CHECK`: daily 22:00.
- `JOB_EXECUTION_CLEANUP`: daily 23:00.
- `STOCK_OPTION_DATA_UPDATE`: label is "Update day time option data".

## Record Alert Scheduler

Core classes:

- `AlertSchedule`: user alert definition tied one-to-one to a `Record`.
- `AlertExecution`: durable send attempt record.
- `ScheduleAlertService`: DB poller and email sender.
- `RecordService`: creates, updates, cancels, and lists alert schedules.
- `ScheduledRecordsPage.vue`: user-facing schedule list under `/tools/scheduled-records`.

Supported schedule types:

- `ONE_TIME`: one future timestamp.
- `RECURRING`: selected weekdays plus a clock time.

Rules:

- A record alert is created only when the request has the `ALERT` label, an `alertType`, and required alert fields.
- Create/update record flows call `ScheduleAlertService.scheduleAlert(...)`, which computes `nextRunAt`, sets `enabled`, and saves the row.
- Delete/cancel flows disable/delete the alert row and clear `nextRunAt`.
- The scheduler polls due alerts every 5 seconds by default (`alerts.scheduler.poll-delay-ms`).
- Successful one-time alerts are disabled after send.
- Successful recurring alerts roll `nextRunAt` forward to the next selected weekday.
- Failed sends record an `AlertExecution` failure and move `nextRunAt` by `alerts.scheduler.retry-delay-minutes`.

Visibility:

- Normal users see only active schedules for records they created.
- Admin users see active schedules for all users, split in the frontend into "My Schedules" and "Other Users' Schedules".
- Expired one-time schedules and disabled schedules are not shown.

## Timezone Rules

The app timezone is `application.time-zone`, sourced from `APP_TIMEZONE` and defaulting to `America/New_York`.

Important rules:

- Frontend alert inputs use local date/time controls and send an ISO timestamp with offset, for example `2026-05-22T15:30:00-04:00`.
- Backend stores `ZonedDateTime` values through JPA/MySQL. Stored/reloaded values may come back with a UTC zone, for example `19:30Z`.
- For one-time alerts, instant comparison is enough.
- For recurring alerts, the scheduler must convert the stored timestamp back into the app timezone before reading hour/minute. Otherwise `15:30 America/New_York` can be treated as `19:30`.
- `ScheduleAlertService.computeNextRunAt` and recurring repair logic enforce this.
- The scheduled-records API converts `timeAt`, `nextRunAt`, and `lastSentAt` to app timezone for dashboard display.
- Frontend display code formats alert schedule times using `APP_TIME_ZONE` from `src/api/config.js`.

Regression test:

- `ScheduleAlertServiceTests.recurringAlertUsesAppLocalClockWhenStoredAsUtc` verifies `2026-05-22T19:30:00Z` schedules as `15:30 America/New_York`.

## Qdrant Jobs

Record create/update/delete and chat-to-record flows queue durable admin-job executions:

- `QDRANT_RECORD_UPSERT`
- `QDRANT_RECORD_DELETE`

These jobs are hidden from the dashboard rows because they are internal consistency work. The dashboard keeps only:

- `QDRANT_CONSISTENCY_CHECK`: count-level status that is good enough for current admin monitoring.

Qdrant calls should stay asynchronous from user workflows. User-facing save/delete should not wait on vector indexing beyond creating the durable execution row.

## Frontend Notes

- Do not define inline Vue child components with `template: "..."` strings in production pages. The app uses the runtime-only Vue build, so those templates are not compiled in production. Put renderable markup in `.vue` SFC templates or create a real child `.vue` component.
- `ScheduledRecordsPage.vue` intentionally renders the schedule table directly in the SFC template for this reason.
- `RecordService.getAlertSchedules()` should not swallow API failures as `[]`; otherwise a backend error looks like "no schedules".

## Where To Change Things

Use these entry points first:

- Add/change an admin job type: `JobType`, a `JobHandler`, `BuiltInJobDefinitions`, dashboard grouping if needed.
- Change job schedule defaults: `BuiltInJobDefinitions`, then redeploy so `BuiltInJobSeeder` resets built-ins.
- Change admin dashboard display: `AdminToolsPage.vue`.
- Change record alert schedule semantics: `ScheduleAlertService`, `AlertScheduleRepository`, `RecordService`, and `RecordEditForm.vue`.
- Change scheduled records display: `ScheduledRecordsPage.vue`.
