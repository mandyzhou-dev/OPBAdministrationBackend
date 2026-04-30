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
- Return login profile data and a JWT.
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

Login logic lives in `UserController.login(...)` (`src/main/java/ca/openbox/user/controller/UserController.java`) and is supported by `SecurityConfiguration`, `UserService`, `UserRepository`, `LoginDTO`, `UserDTO`, and `JwtUtil`. The existing `LoginDTO.username` field accepts either a username or an email address. The full login workflow is documented in the repo-local Codex skill [.codex/skills/opb-login-workflow/SKILL.md](.codex/skills/opb-login-workflow/SKILL.md).

Email-login support is documented in [docs/login-email-support-plan.md](docs/login-email-support-plan.md). The Swagger/OpenAPI contract for the username-or-email login interface is available at [docs/openapi-login.yaml](docs/openapi-login.yaml).

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

Main routes:

- `PUT /api/shift/shiftarrangement`
- `PUT /api/shift/shiftarrangement/batchCreateByDate`
- `PUT /api/shift/shiftarrangement/deleteCurrentShift`
- `PUT /api/shift/shiftarrangement/modifyCurrentShift`
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
- Queue email notifications for handlers when a leave application is submitted.

Important classes:

- `LeaveApplicationController`
- `LeaveApplicationService`
- `ApplicationStatusChangeMessageQueue`
- `EmailNotificationConsumer`

Main routes:

- `PUT /api/process/application/leave-application`
- `POST /api/process/application/{applicationID}/permit`
- `POST /api/process/application/{applicationID}/reject`
- `DELETE /api/process/application/{applicationID}`
- `GET /api/process/application`
- `PUT /api/process/application/{applicationID}/note`

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

There is currently no `src/test` directory, so Maven test execution only verifies compilation unless tests are added.

## Notes for Maintainers

- Most endpoints are currently permitted by `SecurityConfiguration`; JWTs are generated on login but there is no request filter enforcing JWT authentication.
- CORS is mainly configured for `http://localhost:8081`.
- Several workflows depend on hard-coded role/group strings such as `manager`, `tester`, `surrey`, and `public`.
- Leave and resignation notifications use in-memory blocking queues and manually started consumer threads. Queued messages are not durable across process restarts.
- Time handling uses `ZonedDateTime` heavily, with Vancouver-specific logic in schedule copying, preferred dates, and some statistics.
- The README describes the code as it exists now; API behavior should be verified when changing controllers or repository query methods.
