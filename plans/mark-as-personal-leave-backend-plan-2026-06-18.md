# Backend Plan: Schedule Mark As Personal Leave

## Scope

Allow managers to mark an existing shift as personal leave from the Schedule UI.

This is a planning document only. Do not implement code or execute database changes until the user approves the contract.

## Current Project Context

- Project: Spring Boot 3.2.3, Maven, Spring Web, Spring Data JPA, Spring Security.
- Shift status endpoint:
  - Controller: `src/main/java/ca/openbox/shift/controller/ShiftArrangementController.java`
  - Service: `src/main/java/ca/openbox/shift/service/ShiftArrangementService.java`
  - DTO: `src/main/java/ca/openbox/shift/dto/ShiftStatusUpdateDTO.java`
- Status enum/rules:
  - `src/main/java/ca/openbox/shift/entities/ShiftStatus.java`
- Shift persistence:
  - `src/main/java/ca/openbox/shift/dataobject/ShiftArrangementDO.java`
  - table: `opb_shift_arrangement`
  - status column: `status varchar(32)`
- Schedule presentation native queries:
  - `src/main/java/ca/openbox/shift/repository/ShiftPresentationRepository.java`
- Workload/statistics exclusion:
  - `src/main/java/ca/openbox/statistics/service/WorkLoadCalculator.java`
  - uses `ShiftStatus.isNonWorked(...)`
- Existing manual statuses:
  - `no_show`
  - `paid_sick_leave`
  - `unpaid_sick_leave`

## Proposed Backend Contract

Add a new shift status value:

```java
PERSONAL_LEAVE("personal_leave", true, true)
```

Semantics:

- `manualTarget = true`: managers can set it through the existing manual status PATCH endpoint.
- `nonWorked = true`: excluded from workload/KPI calculations the same way sick leave and no-show are excluded.
- No paid sick leave quota validation applies.
- No automatic leave application creation, approval, or linkage is included in this task.

Important naming note: frontend leave applications currently use a separate leave type value `personalleave`. This backend plan intentionally uses `personal_leave` for shift status to match existing shift status naming style.

## API

Reuse the existing endpoint:

```http
PATCH /api/shift/shiftarrangement/{id}/status
Content-Type: application/json

{
  "status": "personal_leave",
  "operatorUsername": "<manager username>"
}
```

Success response: existing `ShiftArrangement` response shape.

Example:

```json
{
  "id": 2811,
  "username": "employee1",
  "start": "2026-06-18T09:30:00-07:00",
  "end": "2026-06-18T18:00:00-07:00",
  "status": "personal_leave",
  "groupName": "surrey"
}
```

Do not create a new endpoint unless product later requires personal-leave-specific metadata such as reason, duration category, or application linkage.

## Validation And Permissions

Existing service validation should remain authoritative:

- Shift must exist.
  - Missing shift -> `404` via `ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found")`.
- Operator must be manager.
  - Non-manager/missing operator -> `403` with message `Only Manager can change shift status`.
- Status must be an allowed manual target.
  - Invalid status -> `400` handled by `ShiftExceptionHandler` as:

```json
{
  "error": "INVALID_SHIFT_REQUEST",
  "message": "Invalid shift status target: <value>"
}
```

Personal leave-specific validation:

- No quota/probation check.
- No applicant ownership check.
- No date range check beyond the shift existing.

Paid sick leave validation must remain scoped only to `paid_sick_leave`.

## Service And Repository Plan

### `ShiftStatus.java`

Add:

```java
PERSONAL_LEAVE("personal_leave", true, true)
```

Because `MANUAL_TARGETS` and `NON_WORKED_STATUSES` are derived from enum flags, this automatically updates:

- `ShiftStatus.isAllowedManualTarget("personal_leave")`
- `ShiftStatus.isNonWorked("personal_leave")`
- `ShiftStatus.nonWorkedValues()`

### `ShiftArrangementService.java`

No new public method required.

Existing `updateStatus(Integer shiftId, String newStatus, String operatorUsername)` should work after enum update:

1. Load shift.
2. Assert manager.
3. Assert manual status target.
4. Apply paid sick leave quota check only when `newStatus.equals("paid_sick_leave")`.
5. Save `shift.status = "personal_leave"`.

Review the code to ensure no new branch accidentally applies paid sick leave quota logic to personal leave.

### `ShiftPresentationRepository.java`

Update every native query status allow-list to include `personal_leave`.

Current filters include:

```sql
('active', 'cancelled', 'no_show', 'paid_sick_leave', 'unpaid_sick_leave')
```

Required filters after implementation:

```sql
('active', 'cancelled', 'no_show', 'paid_sick_leave', 'unpaid_sick_leave', 'personal_leave')
```

This is necessary so personal-leave shifts still appear on manager and employee Schedule views after being marked.

Preferred follow-up improvement: if practical, remove repeated hard-coded status lists and source presentation status filtering from `ShiftStatus`, but keep that refactor small and local.

### DTOs / Entities

No new DTO required.

Existing DTOs remain:

- Request: `ShiftStatusUpdateDTO`
  - `status: String`
  - `operatorUsername: String`
- Response: `ShiftArrangement`
  - `status: String`

`ShiftArrangementDO.status` remains `String`.

## Database Plan

Required database schema change: none.

Reason:

- `opb_shift_arrangement.status` is already a `varchar(32)`.
- The new value `personal_leave` is 14 characters and fits.
- No enum/check constraint was found in the project DDL.
- No new table or column is needed for the requested Schedule action.

Required SQL for this feature:

```sql
-- No required SQL migration.
```

Optional documentation-only SQL if the deployed database column comment still says only `active | cancelled` and the owner wants it updated:

```sql
ALTER TABLE opb_shift_arrangement
  MODIFY COLUMN `status` varchar(32)
  COMMENT 'active | cancelled | no_show | paid_sick_leave | unpaid_sick_leave | personal_leave';
```

The optional SQL changes metadata/comment only and is not required for runtime behavior. Database execution must be done by the user, not by the agent.

Optional data migration, only if product asks to convert existing records manually:

```sql
-- Template only. Fill the WHERE clause with exact approved IDs/dates before execution.
UPDATE opb_shift_arrangement
SET `status` = 'personal_leave'
WHERE id IN (...);
```

Do not run this without explicit user-approved target records.

## Tests

Update or add focused backend tests:

### `ShiftStatusTest`

- `isAllowedManualTarget("personal_leave")` returns true.
- `isNonWorked("personal_leave")` returns true.
- `isAllowedManualTarget("active")` and `isAllowedManualTarget("cancelled")` remain false.

### `ShiftArrangementServiceTest`

- Manager can update a shift to `personal_leave`.
- Saved `ShiftArrangementDO.status` is `personal_leave`.
- Paid sick leave quota repository lookup is not invoked for `personal_leave`.
- Non-manager still receives forbidden behavior.
- Invalid status still receives invalid request behavior.

### `WorkLoadCalculatorTest`

- `personal_leave` is skipped as non-worked.

### Controller/CORS tests

- Existing PATCH controller test may be extended with `personal_leave`.
- Existing CORS PATCH preflight remains valid; no new CORS rule expected.

Suggested focused verification:

```bash
mvn test -Dtest=ShiftStatusTest,ShiftArrangementServiceTest,WorkLoadCalculatorTest,ShiftArrangementControllerCorsTest,SecurityConfigurationTest
```

## Frontend Interaction Requirements

Frontend should send:

```json
{
  "status": "personal_leave",
  "operatorUsername": "<current logged-in username>"
}
```

Frontend should expect:

- Success: updated shift with `status: "personal_leave"`.
- `400 INVALID_SHIFT_REQUEST`: invalid status value.
- `403`: operator is not a manager.
- `404`: shift not found.

Schedule presentation must return personal-leave shifts in the same APIs used today:

- `GET /api/presentor/shift/{username}/findVisibleShifts`
- `GET /api/presentor/shift/{username}/getMyShiftByStartDateScope`

If presentation filters are missed, the PATCH will succeed but the shift can disappear from the Schedule screen.

## Task Decomposition For Backend_Dev

1. Add `PERSONAL_LEAVE("personal_leave", true, true)` to `ShiftStatus`.
2. Update `ShiftPresentationRepository` native query status filters.
3. Confirm `ShiftArrangementService.updateStatus` needs no new branch except preserving paid sick leave-only quota validation.
4. Update focused unit/controller tests listed above.
5. Run focused Maven tests.
6. Report whether optional DB comment SQL should be executed by the user.

