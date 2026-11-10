# UC-403 – Data Backup

## 1. Purpose

The system provides an automated and on-demand backup mechanism for TFHUB business data.

The backup is a portable Excel (`.xlsx`) file containing athlete, training group, test and result data. Its structure must support potential future automated data import into TFHUB.

Backups are encrypted before being uploaded to Google Drive. The system retains only the three most recent successfully uploaded backup files.

The backup mechanism must support:
- On-demand execution by an administrator through a protected REST endpoint.
- Automatic execution once a week.
- A single shared implementation for both execution modes.

Database restoration is explicitly out of scope. The backup is intended to preserve business data and allow its reconstruction independently of the original database.

## 2. Scope

### In scope
- Exporting business data from the TFHUB database.
- Generating a structured XLSX file.
- Adding export metadata and format version.
- Encrypting the generated file.
- Uploading the encrypted file to Google Drive.
- Retaining only the three most recent successfully uploaded backups.
- On-demand backup endpoint restricted to ADMIN users.
- Weekly scheduled backup execution.
- Preventing concurrent backup executions.
- Logging backup execution results and errors.

### Out of scope
- Database dumps and database restoration.
- Exporting user accounts, passwords, sessions or authentication data.
- Backup of HomeCenter or any other database.
- Backup management UI or backup history module.
- Restoring data through an automated import mechanism.
- Restoring individual records through the application.

## 3. Backup File Format

### 3.1. File structure

The backup is an XLSX workbook containing:

**Worksheet: `Wyniki`**

Each row represents exactly one result. Athlete, group and test data are intentionally denormalized and repeated across rows.

The worksheet must contain separate columns for individual attributes. First name and last name must never be combined into one column.

Initial column structure:

| Column | Description |
|---|---|
| athlete_id | Original athlete database ID |
| first_name | Athlete first name |
| last_name | Athlete last name |
| birth_year | Athlete birth year |
| gender | Athlete gender |
| training_group_id | Original training group database ID; nullable |
| training_group_name | Training group name; nullable |
| test_id | Original test database ID |
| test_name | Test name |
| test_category | Test category |
| test_unit | Test measurement unit |
| higher_is_better | Whether a higher result is better |
| result_id | Original result database ID |
| result_date | Result date |
| result_value | Result value |
| result_type | Result type |

The exact fields and data types must be verified against the actual database schema before implementation. Additional business fields required to reconstruct the exported records may be included.

The export must preserve original database IDs to support potential future data import and relationship reconstruction.

All results must be exported, including historical results and results belonging to inactive athletes.

**Worksheet: `Metadata`**

Contains at least:
- `format_version` – backup file format version, initially `1`.
- `exported_at` – timestamp of export generation, using `Europe/Warsaw` timezone.

The metadata format must be extensible.

### 3.2. File naming

The generated file name must contain the full timestamp to avoid collisions between multiple backups generated on the same day.

Format:

`tfhub_backup_YYYY-MM-DD_HH-mm-ss.xlsx`

Example:

`tfhub_backup_2026-11-10_11-41-32.xlsx`

The timestamp must use the `Europe/Warsaw` timezone.

### 3.3. File integrity

The system must not upload an incomplete or invalid XLSX file.

Any failure during export or file generation must terminate the backup operation without uploading the file.

## 4. Google Drive Integration

The system uses `rclone` to access Google Drive.

The destination is a dedicated Google Drive folder:

`TFHUB_BACKUPS`

The exact remote and folder configuration must be externalized and must not be hardcoded in application source code.

Google Drive authentication must use OAuth2 configured through rclone. No user credentials or API secrets may be committed to the repository.

The generated XLSX file must be encrypted client-side before upload, using `rclone crypt`.

Google Drive must contain encrypted backup files, not readable XLSX files.

Encryption configuration and credentials must be stored securely on the Raspberry Pi, with restricted filesystem permissions. The encryption recovery secret must also be stored independently of the Raspberry Pi.

## 5. Backup Execution

### 5.1. Shared implementation

Both execution modes must use the same backup service and processing pipeline:

1. Acquire a mechanism preventing concurrent backup executions.
2. Read required business data from the TFHUB database.
3. Generate the XLSX workbook.
4. Validate the generated file.
5. Encrypt and upload the file to Google Drive.
6. Verify successful upload.
7. Apply retention policy, keeping the three most recent successfully uploaded backups.
8. Remove temporary local files.
9. Log the final execution status.

A failed backup must not cause deletion of existing backups.

Temporary files must be removed after successful or failed execution whenever possible.

### 5.2. On-demand execution

The system exposes:

`POST /api/admin/backup`

Requirements:
- Only authenticated users with role `ADMIN` may invoke the endpoint.
- Authorization must be enforced by the backend.
- Other roles must not be able to execute backups.
- The endpoint invokes the shared backup service.
- The response must report the operation outcome and, on success, the generated file name and execution timestamp.
- Failures must return an appropriate HTTP error response without exposing sensitive configuration details.
- Concurrent execution attempts must be rejected or handled safely without starting a second backup.

The endpoint is synchronous for the initial implementation. The response is returned after the backup operation has completed.

### 5.3. Automatic execution

The system executes a backup once a week, every Sunday at 03:00.

Scheduling uses Spring scheduling (`@Scheduled`) and the same shared backup service as the on-demand endpoint.

The application scheduling timezone must be explicitly configured as `Europe/Warsaw`.

Automatic execution must not require an administrator session or credentials.

If a backup is already running, the scheduled execution must not start another one.

## 6. Retention Policy

The system retains only the three most recent successfully uploaded backup files in the configured backup destination.

Retention rules:
- The newly uploaded backup must be verified before any existing backup is deleted.
- The three newest valid backup files are retained, ordered by their timestamp.
- Older backup files are deleted only after a successful upload.
- If upload or verification fails, existing backups remain untouched.
- The retention operation must only affect files belonging to TFHUB backups in the configured destination.

## 7. Error Handling and Logging

The system must handle and log failures related to:
- Database data retrieval.
- XLSX generation or validation.
- Encryption.
- Google Drive upload.
- Upload verification.
- Retention and deletion of old files.

Logs must include:
- Execution start and finish timestamps.
- Execution source: `MANUAL` or `SCHEDULED`.
- Final status: `SUCCESS` or `FAILURE`.
- Generated file name, if available.
- Error details sufficient for troubleshooting.

Logs must not contain passwords, OAuth tokens, encryption secrets or other sensitive configuration values.

A failure in backup execution must not affect normal TFHUB application operation.

## 8. Security Requirements

- On-demand backup endpoint is ADMIN-only.
- Backend authorization must be enforced independently of frontend visibility.
- Google Drive OAuth configuration and encryption credentials must not be committed to Git.
- Sensitive configuration files on the Raspberry Pi must have restricted filesystem permissions.
- Temporary plaintext XLSX files must be removed after processing.
- Backup contents must be encrypted before leaving the Raspberry Pi.
- Encryption recovery information must be stored separately from the backup files and independently of the Raspberry Pi.

## 9. Acceptance Criteria

1. An ADMIN can trigger a backup through `POST /api/admin/backup`.
2. COACH and ATHLETE users cannot trigger a backup.
3. Unauthenticated requests cannot trigger a backup.
4. The generated XLSX contains all required business data.
5. Each result occupies exactly one row.
6. Individual athlete attributes are stored in separate columns.
7. Original athlete, group, test and result IDs are preserved.
8. Inactive athletes and their historical results are included.
9. The workbook contains a `Metadata` worksheet with format version and export timestamp.
10. Generated filenames contain a timestamp and do not overwrite previous files.
11. The uploaded backup is encrypted and cannot be read as an XLSX directly from Google Drive.
12. A successful upload is verified before retention is applied.
13. Only the three newest successfully uploaded backups are retained.
14. Failed uploads do not cause deletion of existing backups.
15. A weekly backup is automatically triggered every Sunday at 03:00 Europe/Warsaw time.
16. Manual and scheduled execution use the same backup implementation.
17. Concurrent backup executions are prevented.
18. Temporary plaintext files are cleaned up after execution.
19. Failures are logged without exposing secrets.
20. Backup execution does not affect the existing HomeCenter installation or database.

## 10. Implementation Notes

- Before implementation, inspect the actual database schema and existing application architecture. Do not assume column names or types based solely on this document.
- Reuse existing project conventions for REST endpoints, authorization, services, configuration and testing.
- Keep Google Drive and encryption configuration external to application source code.
- Do not introduce unnecessary dependencies or a new backup-management module.
- Keep the implementation focused on the requirements in this UC.
- Do not modify HomeCenter or its configuration.
- Do not commit changes to Git as part of implementation.