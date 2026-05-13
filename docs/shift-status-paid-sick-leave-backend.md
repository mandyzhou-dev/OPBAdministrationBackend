# Shift Status and Paid Sick Leave Backend Notes

Status: implemented on 2026-05-13.

This document records the backend contract and maintenance notes for manual shift status changes, paid sick leave quota, non-worked status filtering, and CORS/Spring Security requirements for browser PATCH endpoints.

## Product Rules

Managers can manually mark a shift as one of three non-worked states:

- `no_show`
- `paid_sick_leave`
- `unpaid_sick_leave`

There is intentionally no reset or `active` write endpoint in this feature. The manual status API must reject `active`, `cancelled`, and arbitrary strings.

`cancelled` is an existing state and remains readable, but it is also treated as non-worked for statistics and KPI.

## Status Semantics

| Status | Meaning | Manual API target | Worked hours | KPI input | Paid sick leave quota |
| --- | --- | --- | --- | --- | --- |
| `active` | Scheduled worked shift | No | Included | Included | Not counted |
| `cancelled` | Existing cancelled shift | No | Excluded | Excluded | Not counted |
| `no_show` | Employee did not attend | Yes | Excluded | Excluded | Not counted |
| `paid_sick_leave` | Paid sick leave | Yes | Excluded | Excluded | Counted by Vancouver calendar day |
| `unpaid_sick_leave` | Unpaid sick leave | Yes | Excluded | Excluded | Not counted |

Shared semantics live in `ca.openbox.shift.entities.ShiftStatus`. Keep new status logic centralized there rather than duplicating string sets across services.

## Backend API Contract

The application has servlet context path `/api`, so controller mappings are served below `/api`.

### Update Shift Status

```text
PATCH /api/shift/shiftarrangement/{id}/status
```

Request body:

```json
{
  "status": "no_show",
  "operatorUsername": "manager_username"
}
```

Allowed `status` values:

- `no_show`
- `paid_sick_leave`
- `unpaid_sick_leave`

Response body is the updated `ShiftArrangement` with the current `status`.

The service checks:

- The shift exists.
- `operatorUsername` belongs to an active Manager user. Current architecture checks `roles` and `groupName`; it is not full JWT authorization.
- The requested status is one of the three manual targets.
- `paid_sick_leave` additionally requires the employee to be eligible and have remaining quota, unless the target Vancouver calendar day is already counted.

### Paid Sick Leave Quota

```text
GET /api/shift/shiftarrangement/{id}/paid-sick-leave-quota?operatorUsername=manager_username
```

Response body:

```json
{
  "username": "employee1",
  "year": 2026,
  "usedDays": 2,
  "quotaDays": 5,
  "probation": false,
  "eligible": true,
  "targetDateAlreadyCounted": false,
  "canMarkPaidSickLeave": true,
  "message": "Used 2/5"
}
```

Quota rules:

- Business date and business year are calculated in `America/Vancouver`.
- The target date is based on the shift start instant converted to Vancouver local date.
- One employee receives 5 paid sick leave calendar days per Vancouver calendar year after probation.
- Multiple `paid_sick_leave` shifts for the same employee on the same Vancouver local date count as 1 used day.
- `UserService.isInProbation(username)` is reused. If `bigDay == null`, the employee is treated as in probation / not eligible.
- `canMarkPaidSickLeave = !probation && (usedDays < 5 || targetDateAlreadyCounted)`.

Do not use database session timezone or MySQL timezone functions for this rule. Convert `ZonedDateTime` values in Java with:

```java
ZoneId businessZone = ZoneId.of("America/Vancouver");
LocalDate businessDate = shift.getStart()
        .withZoneSameInstant(businessZone)
        .toLocalDate();
```

## Statistics and KPI

Worked hours and KPI work-hour inputs must exclude:

- `cancelled`
- `no_show`
- `paid_sick_leave`
- `unpaid_sick_leave`

Current locations:

- `WorkTimeStatisticsPresentor` filters `ShiftPresentation.status` before lunch deduction and aggregation.
- `WorkLoadCalculator` filters `ShiftArrangement.status` before summing work minutes.
- `ShiftArrangementService.getByGroupAndDate(...)` and `getByUserAndGroupAndDate(...)` also filter non-worked statuses for KPI-facing paths.

When adding another statistics or KPI path, reuse `ShiftStatus.isNonWorked(...)`.

## Schedule Projection

Schedule read models must keep non-worked shifts visible so the frontend can show status colors to employees.

Current requirements:

- `ShiftPresentation` includes `status`.
- Native schedule queries select `opb_shift_arrangement.status`.
- Visible shift queries include `active`, `cancelled`, `no_show`, `paid_sick_leave`, and `unpaid_sick_leave`.

Do not filter manual non-worked statuses out of schedule projections; only filter them out of worked-hour and KPI calculations.

## CORS and Spring Security for PATCH Endpoints

Browser `PATCH` calls with JSON trigger an `OPTIONS` preflight. When adding a new browser-facing HTTP method, update every relevant backend gate:

1. `SecurityConfiguration.corsConfigurationSource()` allowed methods.
2. `SecurityConfiguration.securityFilterChain(...)` request matchers, for example `HttpMethod.PATCH, "/shift/**"`.
3. MVC CORS method list in `CORSConfiguration`, if that config remains present.
4. Tests covering both preflight and the actual method.

The 2026-05-13 bug was:

- Frontend correctly called `PATCH /api/shift/shiftarrangement/{id}/status`.
- Browser sent `OPTIONS /api/shift/shiftarrangement/{id}/status`.
- Backend CORS allowed methods did not include `PATCH`.
- Spring Security did not permit `PATCH /shift/**`.
- Result: `403 Invalid CORS request` before the business API was called.

The regression tests are:

```bash
mvn test -Dtest=SecurityConfigurationTest,ShiftArrangementControllerCorsTest
```

## Verification Commands

Run targeted tests for this feature:

```bash
mvn test -Dtest=ShiftStatusTest,ShiftArrangementServiceTest,WorkLoadCalculatorTest
mvn test -Dtest=SecurityConfigurationTest,ShiftArrangementControllerCorsTest
```

Run the full backend build:

```bash
mvn package
```

Browser-level verification should inspect Network for both requests:

- `OPTIONS /api/shift/shiftarrangement/{id}/status` should not return 403.
- `PATCH /api/shift/shiftarrangement/{id}/status` should reach the controller and return the updated status, or a business validation error such as probation/quota/permission.

If `localhost:8080` is still running an old backend process, restart it after rebuilding; otherwise the browser will continue hitting stale CORS/security configuration.

## Database Notes

No database change was required for this feature.

Reason:

- `opb_shift_arrangement.status` already exists.
- Production `status` is `varchar(32)`.
- Production has no enum/check constraint on `status`.
- New status strings fit inside 32 characters.
- Quota is derived from existing shift rows and Java-side Vancouver local date grouping.

If a future environment adds enum/check constraints, stop implementation and provide the full SQL in the issue for the user/DBA to run. Do not modify the database directly.
