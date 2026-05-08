# Recorder Backend

Spring Boot backend for the Recorder app. It serves the Vue frontend in production, manages records/files/labels/users, proxies market-pulse stock and option APIs, and keeps Qdrant embeddings in sync for semantic record search.

## Main Features

- JWT authentication with Spring Security.
- User signup, login, account info, password change, and email-based password reset.
- Role-based access with admin-only endpoints for data update jobs.
- Record CRUD with labels, public/private visibility, metadata, alert scheduling, images, and file attachments.
- File serving with owner/admin/public visibility checks.
- Label lookup APIs.
- Stock daily-history database reads and updates via Market Pulse.
- Option symbols, expiry dates, historical option data, and expired parquet compaction via Market Pulse.
- Qdrant-backed semantic record search. Records are upserted/deleted asynchronously when records change.
- Scheduled stock/option data updates, status emails, and weekly expired option parquet compaction.

## Runtime Configuration

Important environment-backed properties are in `src/main/resources/application.properties`.

| Property | Description |
| --- | --- |
| `APP_ENV` | `DEV` uses local frontend URL; unset or `PROD` uses production frontend URL. |
| `DEV_FRONTEND_URL` | Frontend origin for local reset-password links, default `http://localhost:8080`. |
| `PROD_FRONTEND_URL` | Production frontend origin, default `https://bigbigbun.com`. |
| `DB_HOST`, `DB_USER`, `DB_PASS` | MySQL connection settings. |
| `JWT_SECRET` | Secret for JWT signing. |
| `MAIL_*` | SMTP settings used for alerts, signup emails, and password reset emails. |
| `BACKEND_APP_LOG_PATH` | Application log directory. |
| `BACKEND_APP_FILE_PATH` | Uploaded file storage directory. |
| `MARKET_PULSE_URL` | Market Pulse API base URL used by backend services. |
| `QDRANT_COLLECTION` | Qdrant collection name, default `records_chunks`. |

`spring.jpa.hibernate.ddl-auto=update` is enabled, so new JPA tables such as `password_reset_tokens` are created automatically without dropping existing data.

## Build

From the repository root:

```bash
mvn -pl recorder-backend -am compile -DskipTests
```

The root `build.sh` can also build frontend assets, backend JAR, Docker images, and start compose services.

## Authentication

All `/api/**` endpoints require JWT authentication except the public auth endpoints listed below. Send the JWT as:

```text
Authorization: Bearer <token>
```

Admin-only endpoints require the authenticated user to have the admin role.

## API Reference

### Auth

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/auth/authenticate` | Public | Login with username/password and return a JWT token. |
| `POST` | `/api/auth/signup` | Public | Create a new user with default USER role and send signup email. |
| `GET` | `/api/auth/user-info` | Authenticated | Return current user's username, email, creation time, and admin flag. |
| `POST` | `/api/auth/change-password` | Authenticated | Change current user's password after validating current password. |
| `POST` | `/api/auth/forgot-password` | Public | Queue a generic password reset email for an account email if it exists. |
| `POST` | `/api/auth/reset-password` | Public | Reset password with a valid unexpired reset token. |
| `GET` | `/reset-password?token=...` | Public | Redirect browser to the Vue hash route for password reset. |

### Records

Base path: `/api/records`

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/create-record` | Authenticated | Create a record with labels, metadata, optional alert schedule, images, and files. Multipart form data. |
| `POST` | `/update-record` | Owner/Admin | Update a record, labels, metadata, files, and alert schedule. Multipart form data. |
| `GET` | `/record/{id}` | Visible to user | Get one record by id. Private records require owner/admin; public records are visible to authenticated users. |
| `GET` | `/delete-record/{id}` | Owner/Admin | Delete a record and remove its Qdrant vectors if present. |
| `POST` | `/list-records` | Authenticated | List records with filters for labels, excluded labels, title, date ranges, public flag, owner-only flag, paging, and sorting. |
| `POST` | `/record-count-by-date-label-in-range` | Authenticated | Return daily record counts in a date range. Dates must be `yyyy-MM-dd`. |
| `POST` | `/get-records-by-description` | Authenticated | Semantic search records through Qdrant using query text, threshold, and limit. |

### Files

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `GET` | `/api/recfile/{fileID}` | Visible to user | Serve an uploaded file after owner/admin/public visibility validation. |

### Labels

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `GET` | `/api/labels/all-labels` | Authenticated | Return all labels. |
| `GET` | `/api/labels/label-exists/{label}` | Authenticated | Return whether a label exists. |

### Stock Data

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/stock-data/get-daily-history` | Admin | Read stock daily history from backend database for requested symbols. |
| `POST` | `/api/stock-data/update-daily-history-db` | Admin | Fetch stock daily history from Market Pulse and update backend database. |
| `GET` | `/api/stock-data/update_stock_option_data` | Admin | Trigger Market Pulse stock/option update job. |
| `GET` | `/api/stock-data/update_stock_data/help` | Admin | Return help text for stock/option update job arguments. |

### Option Data

Base path: `/api/option-data`

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `GET` | `/symbols` | Authenticated | Return option symbols available in Market Pulse parquet data. |
| `POST` | `/expiries` | Authenticated | Return expiry dates for a symbol. Request body includes `symbol`. |
| `POST` | `/history` | Authenticated | Return option history for `symbol`, `expiry`, and `optionType`. |
| `GET` | `/combine-expired-parquet` | Admin | Trigger Market Pulse compaction of expired option parquet files. |

## Market Pulse and Qdrant Integration

`MarketPulseApiService` calls the Python Market Pulse service for:

- stock daily history
- stock/option update jobs
- option symbols, expiries, and history
- expired option parquet compaction

`QdrantEmbeddingService` calls Market Pulse `/qdrant` endpoints for:

- upserting vectors when records are created or updated
- deleting vectors when records are deleted
- querying similar records for semantic search
- checking whether vectors exist for a record

## Scheduled Jobs

Defined in `CronService`.

| Schedule | Job |
| --- | --- |
| Daily `10:05`, `13:30`, `16:30`, `21:00` | Update option data through Market Pulse. |
| Daily `16:30`, `21:00` | Update stock daily history database. |
| Daily `08:00`, `12:00`, `16:00`, `20:00`, `23:00` | Send server status email to admin. |
| Friday `21:30` | Combine expired option parquet files through Market Pulse. |

## Notes

- Password reset tokens are stored as SHA-256 hashes and expire after 30 minutes.
- Password reset API responses are generic to avoid confirming whether an email exists.
- Password reset emails are queued asynchronously so SMTP timing is not exposed to the frontend.
- Uploaded image and file size limits are configured by `recfile.image.file.size` and `recfile.regular.file.size`.
