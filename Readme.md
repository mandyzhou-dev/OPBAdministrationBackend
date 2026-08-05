# OPB Administration Backend

Spring Boot backend for OpenBox administration workflows. The service exposes REST APIs for user registration and login, shift scheduling, preferred workday selection, KPI calculations, announcements, regulations, leave applications, resignations, employment termination, and work-time statistics.

## Technology Stack

- Java and Spring Boot 3.2.3
- Maven
- Spring Web
- Spring Data JPA / Hibernate
- Spring Security
- MySQL
- JJWT for token generation
- Jasypt for encrypted configuration values
- Lombok
- Java Mail and HTTP webhook email integrations

Spring Boot 3 requires Java 17 or newer.

## Runtime Overview

The application starts from `ca.openbox.Main`.

```text
HTTP client
  -> REST controller / presentor
  -> service / application service
  -> Spring Data repository
  -> MySQL tables or read projections
```

The server uses `/api` as the servlet context path, so controller paths are served below `/api`. For example, `UserController` path `user/login` is available as `/api/user/login`.

`Main` enables:

- Spring Boot auto-configuration
- encrypted properties through Jasypt
- scheduled jobs through `@EnableScheduling`

The application sets `spring.config.location` to `file:/etc/openbox/config.yml` by default. A local `src/main/resources/application.yml` exists as a template, but production-like runs are expected to provide `/etc/openbox/config.yml`.

## Configuration

Required configuration keys:

```yaml
spring:
  datasource:
    url: jdbc:mysql://...
    username: ...
    password: ...
  jackson:
    deserialization:
      adjust-dates-to-context-time-zone: false
  jpa:
    show-sql: true

server:
  servlet:
    context-path: /api

secret:
  key: ...
  iv: ...
  jwt: ...

mail:
  clientID: ...
  clientSC: ...
  tenantID: ...
  token: ...
  scope: ...
  sender: ...
  host: ...
  url: ...
  webtoken: ...
```

`secret.key` and `secret.iv` are used by `Cryptor` for AES/CBC/PKCS5Padding encryption of sensitive user data such as SIN values. Mail values can be encrypted with Jasypt using `ENC(...)` syntax.

Database-backed application variables are also required for several workflows:

- `shift_select_month`: current preferred-workday board month.
- `SprintBiweekStartDate`: start date for the current KPI biweekly sprint, formatted as `yyyy-MM-dd`.
- `TVWorkRate`: KPI target rate.
- `TVBonusRate`: KPI bonus rate.
- `PROBATION_MONTHS`: number of months used for probation checks.

## Project Structure

```text
src/main/java/ca/openbox
  Main.java
  batch/                  Scheduled batch jobs
  employment/             Employment termination records
  forum/                  Announcements and read logs
  infrastructure/         Email, JWT, encryption, CORS, application variables
  process/                Leave application workflow
  regulation/             Regulation content
  resignation/            Resignation workflow
  shift/                  Shift scheduling, preferred workdays, KPI, holidays
  statistics/             Work-time aggregation
  user/                   Users, registration, login, user projections

src/main/resources
  application.yml         Configuration template
  ddl/                    Partial schema snippets
```

The codebase generally uses this pattern:

- `controller`: mutable REST API endpoints.
- `presentor`: read-oriented REST API endpoints backed by projection repositories.
- `service`: domain operations and transaction logic.
- `application`: cross-service orchestration.
- `repository`: Spring Data repository interfaces.
- `entities`: domain models used by controllers and services.
- `dataobject`: JPA persistence models.
- `dto`: request/response transfer objects.
- `presentation`: JPA read projections for joined or presentation-specific queries.

## Modules

### User

Package: `ca.openbox.user`

Responsibilities:

- Register users after email verification.
- Authenticate username/password through Spring Security.
- Return minimal login session data and a JWT.
- Encrypt and decrypt SIN values through `Cryptor`.
- Reset passwords and modify profile fields.
- Deactivate users when employment ends.
- Expose read projections for users by role or group.

Important classes:

- `UserController`
- `UserService`
- `SecurityConfiguration`
- `UserRepository`
- `UserPresentationRepository`

Login logic lives in `UserController.login(...)` (`src/main/java/ca/openbox/user/controller/UserController.java`) and is supported by `SecurityConfiguration`, `UserService`, `UserRepository`, `LoginDTO`, `LoginResponseDTO`, and `JwtUtil`. The existing `LoginDTO.username` field accepts either a username or an email address. The full login workflow is documented in the repo-local Codex skill [.codex/skills/opb-login-workflow/SKILL.md](.codex/skills/opb-login-workflow/SKILL.md).

Email-login support is documented in [docs/login-email-support-plan.md](docs/login-email-support-plan.md). The Swagger/OpenAPI contract for the username-or-email login interface is available at [docs/openapi-login.yaml](docs/openapi-login.yaml).

Employee probation checks and email dispatch workflows are documented in the repo-local Codex skill [.codex/skills/opb-employee-probation-email-workflows/SKILL.md](.codex/skills/opb-employee-probation-email-workflows/SKILL.md).

Backend API changes that touch permissions, frontend contracts, filters, pagination, or database implications should follow the repo-local Codex skill [.codex/skills/opb-backend-api-boundary-workflow/SKILL.md](.codex/skills/opb-backend-api-boundary-workflow/SKILL.md).

Leave application DatePicker backend support is documented in [docs/leave-datepicker-backend.md](docs/leave-datepicker-backend.md), and the cross-stack MAN-19 architecture summary is documented in [docs/leave-datepicker-cross-stack-architecture.md](docs/leave-datepicker-cross-stack-architecture.md). Use the repo-local skills [.codex/skills/opb-leave-datepicker-backend-workflow/SKILL.md](.codex/skills/opb-leave-datepicker-backend-workflow/SKILL.md) and [.codex/skills/opb-leave-datepicker-cross-stack-architecture/SKILL.md](.codex/skills/opb-leave-datepicker-cross-stack-architecture/SKILL.md) before future leave DatePicker planning, review, or documentation.

Main routes:

- `POST /api/user/login`
- `POST /api/user/send_code`
- `POST /api/user/register?code=...`
- `GET /api/user/check_validation?username=...`
- `POST /api/user/verify_password`
- `POST /api/user/{username}/password`
- `POST /api/user/{username}/modifyprofile`
- `GET /api/user/{username}/getSinno`
- `GET /api/user/{username}/probation`
- `GET /api/presentor/user/getUserByRoleName?role=...`
- `GET /api/presentor/user/getUserByGroupName?group=...`
- `GET /api/presentor/user/employees/basic`
- `GET /api/presentor/user/employees/options?activeOnly=true`

### Shift Scheduling

Package: `ca.openbox.shift`

Responsibilities:

- Create, modify, and delete shifts.
- Batch-create daily shifts for multiple users.
- Query visible shifts by date range, home group, and employee.
- Track employee preferred workdays by month.
- Copy one week of schedules to another week.
- Serve statutory holidays.

Important classes:

- `ShiftArrangementController`
- `ShiftArrangementService`
- `ShiftBoardController`
- `EmployeePreferWorkdayBoardService`
- `ShiftPresentor`
- `WeekScheduleService`
- `StatutoryHolidayController`

排班逻辑（增删改查） lives in `src/main/java/ca/openbox/shift/controller/ShiftArrangementController.java`, backed by `src/main/java/ca/openbox/shift/service/ShiftArrangementService.java` and `src/main/java/ca/openbox/shift/repository/ShiftArrangementRepository.java`. The request DTOs are `ShiftArrangementDTO` for single-shift create/update/delete and `BatchCreateShiftByDateDTO` for batch daily creation. Read/query scheduling endpoints are exposed separately through `src/main/java/ca/openbox/shift/presentor/ShiftPresentor.java`.

Manual shift status changes and paid sick leave quota are documented in [docs/shift-status-paid-sick-leave-backend.md](docs/shift-status-paid-sick-leave-backend.md). Key rules:

- Managers may write only `no_show`, `paid_sick_leave`, `unpaid_sick_leave`, or `personal_leave` through the manual status API; there is no reset/active write endpoint for this feature.
- `personal_leave` is a shift status value in `ShiftStatus` with `manualTarget = true` and `nonWorked = true`; keep the snake_case value distinct from any frontend leave-application type naming.
- Manual status updates reuse `PATCH /api/shift/shiftarrangement/{id}/status` with request body `{ "status": "<target_status>", "operatorUsername": "<manager username>" }`.
- `paid_sick_leave` quota is calculated by distinct `America/Vancouver` calendar days and calendar years. Multiple paid sick leave shifts for the same employee on the same Vancouver local date count as 1 used day.
- Paid sick leave quota and probation validation apply only when the target status is `paid_sick_leave`; do not apply that quota path to `personal_leave`.
- `bigDay == null` is treated as probation / not eligible for paid sick leave.
- `cancelled`, `no_show`, `paid_sick_leave`, `unpaid_sick_leave`, and `personal_leave` are non-worked statuses for worked hours and KPI input.
- Schedule projections must still return non-worked statuses so the frontend can show status colors to employees. When adding a visible shift status, update every `ShiftPresentationRepository` native query status allow-list in the same change; otherwise a successful status PATCH can make the shift disappear from Schedule reads.
- No database table, field, constraint, or required migration was needed for `personal_leave`; `opb_shift_arrangement.status` is a `varchar(32)` and the new value fits.
- The `personal_leave` backend change was verified with `mvn -Dtest=ShiftStatusTest,ShiftArrangementServiceTest test`, `mvn test` (92 tests, 0 failures / 0 errors), and `mvn -DskipTests package`.
- Browser-facing `PATCH` endpoints must update Spring Security CORS allowed methods, the security method allowlist, MVC CORS methods, and preflight tests.

Select shift form candidate state is documented in [docs/shift-candidates-endpoint-backend.md](docs/shift-candidates-endpoint-backend.md). Key rules:

- `GET /api/shift/shiftarrangement/candidatesByDate` is a read-only DTO endpoint for frontend scheduling candidate state.
- `ShiftCandidateDTO` returns `username`, `name`, `groupName`, `preferred`, `alreadyScheduled`, `existingShiftId`, and `existingShiftStatus`.
- `preferred` means the employee preference board marks the employee as preferring to work on the selected date.
- `alreadyScheduled` means the employee already has a shift on the selected `America/Vancouver` business date; frontend selection and submit flows should treat this as disabled.
- If both flags are true, return both facts and let the frontend apply display priority: `Already scheduled > Selected > Preferred > Normal available`.
- Do not operate on the database directly for this workflow. If future changes need a table, field, constraint, or data migration, first provide complete SQL in the issue for the user to execute.

Copy shifts statutory holiday handling is documented in [plans/copy-shifts-statutory-holiday-backend-plan-2026-05-26.md](plans/copy-shifts-statutory-holiday-backend-plan-2026-05-26.md). Key rules:

- Backend validation is authoritative for `POST /api/shift/preset`; frontend warnings and disabled UI states are useful UX, but they must not be the only protection against illegal holiday inserts.
- All copied-shift holiday checks use the target shift's `America/Vancouver` business date. Convert instants to Vancouver local dates before comparing with `opb_statutory_holiday.statutoryDate`.
- Keep the copy request shape compatible. Do not require new request fields for this workflow unless a future product change explicitly needs them.
- Keep the response backward compatible by preserving `created`, `skipped`, and `overwritten`; add optional structured fields such as `skippedDetails` for richer partial-success reporting.
- Filter generated copy candidates before `shiftArrangementRepository.saveAll(...)`. Holiday candidates should be skipped and reported, not inserted and not left for the database to reject.
- If every generated candidate is skipped, avoid calling `saveAll` with holiday rows; returning `created = 0` plus `skippedDetails` is the expected behavior.
- `STATUTORY_HOLIDAY` skipped details should include enough context for the frontend to explain the partial success, such as `username`, `groupName`, `sourceDate`, `targetDate`, `reason`, and a clear message.
- Preserve existing hard-fail behavior for unrelated guards such as invalid schedule range or target-week duplicate schedules.
- DB rule: agents must not directly change schema, constraints, or production data. If a future copy-shifts fix requires a table, field, constraint, migration, or data repair, first post the complete SQL in the issue for the user to review and execute. The 2026-05-26 statutory holiday copy fix required no DB change.

Main routes:

- `PUT /api/shift/shiftarrangement`
- `PUT /api/shift/shiftarrangement/batchCreateByDate`
- `PUT /api/shift/shiftarrangement/deleteCurrentShift`
- `PUT /api/shift/shiftarrangement/modifyCurrentShift`
- `PATCH /api/shift/shiftarrangement/{id}/status`
- `GET /api/shift/shiftarrangement/{id}/paid-sick-leave-quota?operatorUsername=...`
- `GET /api/shift/shiftarrangement/candidatesByDate?date=...&groupName=...&role=tester`
- `GET /api/presentor/shift/getShiftByStartDateScope`
- `GET /api/presentor/shift/{username}/getMyShiftByStartDateScope`
- `GET /api/presentor/shift/{username}/findVisibleShifts`
- `GET /api/shift/shiftboard/getBoardByDate`
- `GET /api/shift/shiftboard/getBoardByUser`
- `PUT /api/shift/shiftboard/updateBoard`
- `PUT /api/shift/shiftboard/shiftToNextMonth`
- `GET /api/shift/shiftboard/getCurrentMonth`
- `POST /api/shift/preset`
- `GET /api/shift/statutory-holidays`

## Cross-Stack Planning Notes

Reusable Fullstack Architect notes for Application History planning, UI scope control, API boundaries, database-change handling, and verification are captured in [plans/fullstack-architect-reusable-notes-2026-05-20.md](plans/fullstack-architect-reusable-notes-2026-05-20.md).

The same notes now include the Select Shift Form candidate availability workflow: confirm UI intent first, produce the plan before implementation, define the front/back DTO contract first, and never apply DB schema/data changes directly. If a schema change is needed, agents must give the user complete SQL to execute.

The same notes now include MAN-36 Schedule `personal_leave` lessons: keep the status naming contract explicit, update presentation query allow-lists for every visible status, scope paid sick leave quota only to `paid_sick_leave`, state the no-DB-change conclusion, and record final frontend color decisions after product review. The backend-specific plan is [plans/mark-as-personal-leave-backend-plan-2026-06-18.md](plans/mark-as-personal-leave-backend-plan-2026-06-18.md).

### KPI

Package: `ca.openbox.shift.application` and `ca.openbox.shift.service.KPI`

Responsibilities:

- Calculate daily and biweekly KPI targets from scheduled work hours.
- Calculate user-specific or group-wide KPI values.
- Manage target and bonus rates through application variables.
- Store and update KPI records.
- Refresh biweekly KPI records by scheduled job.

Important classes:

- `KPIController`
- `KPIApplication`
- `KPICalculator`
- `KPIRecordController`
- `KPIRecordService`
- `BiweeklyKPIRefreshBatch`

Main routes:

- `GET /api/shift/kpi/groupName`
- `GET /api/shift/kpi/user`
- `GET /api/shift/kpi/groupName/biweek`
- `GET /api/shift/kpi/user/biweek`
- `GET /api/shift/kpi/target-rate`
- `PUT /api/shift/kpi/target-rate`
- `GET /api/shift/kpi/bonus-rate`
- `PUT /api/shift/kpi/bonus-rate`
- `GET /api/shift/kpi-record?year=...`
- `POST /api/shift/kpi-record`
- `PUT /api/shift/kpi-record/{id}`

`BiweeklyKPIRefreshBatch` runs every Sunday at 03:00 and records a new biweekly KPI record when the current date is a 14-day boundary from `SprintBiweekStartDate`.

### Leave Applications

Package: `ca.openbox.process`

Responsibilities:

- Submit leave applications.
- Approve, reject, delete, and annotate applications.
- Query applications by handler, applicant, or all non-pending applications.
- Query manager-visible History as a paged list, optionally filtered by employee.
- Queue email notifications for handlers when a leave application is submitted.

Important classes:

- `LeaveApplicationController`
- `LeaveApplicationService`
- `ApplicationHistoryAccessPolicy`
- `ApplicationStatusChangeMessageQueue`
- `EmailNotificationConsumer`

Admin History employee filtering is documented in the repo-local Codex skill [.codex/skills/opb-backend-api-boundary-workflow/SKILL.md](.codex/skills/opb-backend-api-boundary-workflow/SKILL.md). Current contract:

- `GET /api/process/application/history`
- Required query param: `operatorUsername`
- Optional query params: `employeeUsername`, `page`, `size`, `sort`
- Blank or missing `employeeUsername` means all employees.
- Response is a paged wrapper with `content`, `page`, `size`, `totalElements`, `totalPages`, and `sort`; no results return an empty `content` array.
- Current access is Manager-only through `ApplicationHistoryAccessPolicy`; the policy boundary is reserved for future visibility scopes such as Team Leader team-member access.
- No database table, field, constraint, or data migration is required for this feature.

Leave application decision comments use the neutral `reviewComment` field. This replaced the old rejection-only `rejectReason` meaning so approved applications can also carry conditional approval notes.

- `reason` is still the employee-submitted application reason.
- `reviewComment` is the manager decision comment for both approve and reject.
- `note` is still the editable post-decision History note.
- `POST /api/process/application/{applicationID}/permit` accepts an optional JSON body: `{ "reviewComment": "Approved, but please complete handoff." }`. The body may be omitted.
- `POST /api/process/application/{applicationID}/reject` accepts the same JSON shape, but `reviewComment` is required and blank or whitespace-only comments return `400 Bad Request`.
- Read responses from application list, applicant, handler, and History APIs expose `reviewComment`, not `rejectReason`.
- Only `reviewComment` may be trimmed, and only for blank validation/storage. Do not trim `applicant`, `currentHandler`, `operatorUsername`, `employeeUsername`, or other identity/query values. Usernames such as `Harsimranjit Kaur ` may intentionally contain a trailing space and must be preserved exactly.
- Backend and frontend must use the JSON decision-comment contract together; the old plain text reject body is intentionally replaced.

Database migration for this semantic rename is user-executed only. Agents must not run these SQL statements directly. Use a coordinated release window because old code expects `reject_reason` while migrated code expects `review_comment`.

```sql
-- Pre-check
SHOW COLUMNS FROM opb_leave_application LIKE 'reject_reason';
SHOW COLUMNS FROM opb_leave_application LIKE 'review_comment';

-- Migration
ALTER TABLE opb_leave_application
  RENAME COLUMN reject_reason TO review_comment;

ALTER TABLE opb_leave_application
  MODIFY COLUMN review_comment TEXT NULL;

-- Verification
SHOW COLUMNS FROM opb_leave_application LIKE 'review_comment';
SELECT id, status, review_comment
FROM opb_leave_application
WHERE review_comment IS NOT NULL
ORDER BY submit_time DESC
LIMIT 20;
```

Fallback for older MySQL versions:

```sql
ALTER TABLE opb_leave_application
  CHANGE COLUMN reject_reason review_comment TEXT NULL;
```

Leave DatePicker backend support is documented in [docs/leave-datepicker-backend.md](docs/leave-datepicker-backend.md) and the repo-local Codex skill [.codex/skills/opb-leave-datepicker-backend-workflow/SKILL.md](.codex/skills/opb-leave-datepicker-backend-workflow/SKILL.md). Current contract:

- `GET /api/process/application/leave-date-availability?applicant=<username>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>` returns per-date shift availability for one applicant.
- Request and response availability dates are Vancouver business dates formatted as `YYYY-MM-DD`; the response includes `businessZone: "America/Vancouver"`.
- Each response date contains `date`, `scheduled`, and `shiftIds`.
- Leave submission remains `PUT /api/process/application/leave-application` with existing `start` and `end` zoned datetime fields.
- Backend submit validation is authoritative: any selected Vancouver business date before today is rejected, and `SICK` leave requires an existing applicant shift on every selected Vancouver business date.
- Time input remains the existing frontend manual `HHmm-HHmm` range. No backend TimePicker field, split time field, DTO change, entity change, or migration is part of this DatePicker work.
- No database table, field, constraint, or data migration is required for this feature.

Sick leave proof upload backend notes:

- Upload endpoint: `POST /api/process/application/{applicationID}/sick-proof` with multipart field `proof` and request param `applicant`.
- Files are stored through `SickLeaveProofStorageService` under the configured `uploads.sick-proof-dir`; do not hard-code a local path in controller or service code.
- Store proof state and latest upload metadata in the proof persistence model (`LeaveApplicationProofDO` / `opb_leave_application_proof`): `status`, `uploadedAt`, `originalFilename`, `storedFilename`, `contentType`, and `fileSizeBytes`.
- Return the updated `LeaveApplication` from the upload endpoint so callers can immediately refresh `sickProofRequired`, `sickProofSubmitted`, `sickProofUploadedAt`, and `sickProofOriginalFilename`.
- Keep upload semantics ordered: validate application/applicant, write the file, save proof metadata, then enqueue `SICK_PROOF_UPLOADED`. Email failures must not roll back a successful proof upload.
- Email notifications should reuse `ApplicationStatusChangeMessageQueue`, `LeaveApplicationEmailEvent`, `EmailNotificationConsumer`, and `WebhookEmailService`; avoid adding another email stack for this workflow.
- `LEAVE_SUBMITTED` sends handler review email and, for sick leave, an employee proof reminder email. `SICK_PROOF_UPLOADED` uses a dedicated HR recipient source rather than `currentHandler`; current HR usernames are `raynold` and `agnes`.
- Username lookup and authorization comparison are different concerns: lookup must try the raw username first, then trim fallback; authorization comparisons may normalize both sides for leading/trailing whitespace.
- Any multi-recipient email path must keep the existing 20-second delay between send attempts to avoid webhook/mail limits. This includes handler review emails and sick-proof HR upload notifications.
- Format leave times for email in `America/Vancouver` before rendering with `MMM d, yyyy h:mm a` and `Locale.US`; do not show raw UTC instants in email bodies.
- Startup wiring matters for background consumers. If adding test-only constructors, mark the Spring runtime constructor explicitly with `@Autowired` and keep a wiring regression test so the app does not fail with `No default constructor found`.
- Useful focused tests before handing off: `EmailNotificationConsumerHandlerLookupTest`, `EmailNotificationConsumerTimezoneTest`, `EmailNotificationConsumerSpringWiringTest`, `LeaveApplicationServiceSickProofTest`, and storage/config tests. Also run `mvn test`, `mvn package -DskipTests`, and a short startup smoke test when constructor wiring or application context behavior changed.
- Do not modify production DB data directly. If a future proof workflow needs schema or data changes, provide SQL in the issue for the user to execute.

Main routes:

- `PUT /api/process/application/leave-application`
- `GET /api/process/application/leave-date-availability?applicant=...&from=YYYY-MM-DD&to=YYYY-MM-DD`
- `POST /api/process/application/{applicationID}/permit` with optional JSON `{ "reviewComment": "..." }`
- `POST /api/process/application/{applicationID}/reject` with required JSON `{ "reviewComment": "..." }`
- `DELETE /api/process/application/{applicationID}`
- `GET /api/process/application`
- `GET /api/process/application/history?operatorUsername=...&employeeUsername=...&page=0&size=20&sort=submitTime,desc`
- `PUT /api/process/application/{applicationID}/note`
- `POST /api/process/application/{applicationID}/sick-proof`

### Resignations and Employment

Packages: `ca.openbox.resignation`, `ca.openbox.employment`

Responsibilities:

- Submit and review resignation applications.
- Prevent duplicate active resignation applications for the same applicant.
- Send resignation notifications to managers.
- Create employment termination records.
- Deactivate users after termination.

Important classes:

- `ResignationApplicationController`
- `ResignationApplicationService`
- `ResignationEmailConsumer`
- `EmploymentController`
- `EmploymentService`

Main routes:

- `POST /api/resignations`
- `GET /api/resignations`
- `PUT /api/resignations/{id}`
- `GET /api/resignations/{applicant}`
- `POST /api/employment/{username}/terminate`
- `GET /api/employment/{username}/employment`

### Announcements

Package: `ca.openbox.forum`

Responsibilities:

- Create, update, delete, and fetch announcements.
- Filter visible announcements by user group.
- Track announcement read logs per user.

Important classes:

- `AnnouncementController`
- `AnnouncementService`
- `AnnouncementReadLogService`

Main routes:

- `POST /api/announcement`
- `GET /api/announcement?expireAfter=...&username=...`
- `GET /api/announcement/{announcementId}`
- `PUT /api/announcement/{announcementId}`
- `DELETE /api/announcement/{announcementId}`
- `GET /api/announcement/readLog?reader=...`
- `POST /api/announcement/{announcementId}/read`

### Regulations

Package: `ca.openbox.regulation`

Responsibilities:

- Fetch regulation content.
- Update regulation title, content, and modified time.

Main routes:

- `GET /api/regulation/{regulationId}`
- `PUT /api/regulation/{regulationId}`

### Statistics

Package: `ca.openbox.statistics`

Responsibilities:

- Aggregate scheduled work time by employee over a date range.
- Convert shift durations to total minutes and hours.
- Apply lunch deduction rules.

Main route:

- `GET /api/presentor/statistic/work-time-statistic?start=...&end=...`

### Infrastructure

Package: `ca.openbox.infrastructure`

Responsibilities:

- `WebhookEmailService`: sends email through a configured HTTP webhook.
- `EmailService`: obtains OAuth2 access tokens and contains a commented SMTP/Graph mail implementation.
- `JwtUtil`: generates and validates JWTs.
- `Cryptor`: AES encryption/decryption.
- `ApplicationVariableService`: reads and writes key-value settings in `opb_application_variables`.
- `SecurityConfiguration`: configures CORS, password encoding, authentication, and broad endpoint permissions.

The complete email dispatch workflow is documented in [.codex/skills/opb-employee-probation-email-workflows/SKILL.md](.codex/skills/opb-employee-probation-email-workflows/SKILL.md).

## Database

The application uses JPA entities mapped to tables including:

- `opb_user`
- `opb_email_verification`
- `opb_shift_arrangement`
- `opb_employee_prefer_workday`
- `opb_statutory_holiday`
- `opb_kpi_records`
- `opb_leave_application`
- `opb_resignations`
- `opb_employment_record`
- `opb_announcement`
- `opb_announcement_readlog`
- `opb_regulation`
- `opb_application_variables`

`src/main/resources/ddl` contains partial schema snippets for users and shifts only. The full schema is inferred from JPA models and repositories.

## Local Development

Prerequisites:

- JDK 17+
- Maven
- MySQL
- A config file at `/etc/openbox/config.yml`, or an override of `spring.config.location`

Build:

```bash
mvn clean package
```

Run:

```bash
mvn spring-boot:run
```

Run with a local config file:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:./src/main/resources/application.yml"
```

Run tests:

```bash
mvn test
```

Run targeted shift status / paid sick leave / CORS tests:

```bash
mvn test -Dtest=ShiftStatusTest,ShiftArrangementServiceTest,WorkLoadCalculatorTest
mvn test -Dtest=SecurityConfigurationTest,ShiftArrangementControllerCorsTest
```

Run targeted select shift candidate endpoint tests:

```bash
mvn test -Dtest=ShiftArrangementServiceTest,ShiftArrangementControllerCorsTest
```

Run targeted copy-shifts statutory holiday tests:

```bash
mvn test -Dtest=WeekScheduleServiceTest
mvn package
```

Run targeted leave DatePicker backend tests:

```bash
mvn test -Dtest=LeaveApplicationServiceDateAvailabilityTest,LeaveApplicationControllerAvailabilityTest
mvn clean package
```

The fixed dates used in these tests are fixtures for deterministic assertions; runtime leave-date validation uses the current `America/Vancouver` business date.

## Notes for Maintainers

- Most endpoints are currently permitted by `SecurityConfiguration`; JWTs are generated on login but there is no request filter enforcing JWT authentication.
- CORS is mainly configured for `http://localhost:8081`. When adding browser-facing methods such as `PATCH`, update Spring Security CORS allowed methods, Spring Security request matchers, MVC CORS methods, and preflight tests together.
- Several workflows depend on hard-coded role/group strings such as `manager`, `tester`, `surrey`, and `public`.
- Leave and resignation notifications use in-memory blocking queues and manually started consumer threads. Queued messages are not durable across process restarts.
- Time handling uses `ZonedDateTime` heavily, with Vancouver-specific logic in schedule copying, preferred dates, statistics, KPI input, and paid sick leave quota.
- The README describes the code as it exists now; API behavior should be verified when changing controllers or repository query methods.
