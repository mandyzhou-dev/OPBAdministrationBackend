# Shift Candidate Endpoint Backend Notes

This note captures the backend contract and implementation rules for the read-only scheduling candidate endpoint used by the select shift form.

## Endpoint Contract

- Route: `GET /api/shift/shiftarrangement/candidatesByDate`
- Controller: `src/main/java/ca/openbox/shift/controller/ShiftArrangementController.java`
- Service: `src/main/java/ca/openbox/shift/service/ShiftArrangementService.java`
- DTO: `src/main/java/ca/openbox/shift/dto/ShiftCandidateDTO.java`

Query parameters:

- `date`: required `ZonedDateTime`. The service converts it to the `America/Vancouver` business date and checks the full local day.
- `groupName`: currently accepted by the controller for frontend compatibility. Confirm filtering requirements before using it to change behavior.
- `role`: optional, defaults to `tester`; blank values also fall back to `tester`.

Response is a list of compact candidate DTOs:

```json
[
  {
    "username": "alice",
    "name": "Alice Chen",
    "groupName": "surrey",
    "preferred": true,
    "alreadyScheduled": false,
    "existingShiftId": null,
    "existingShiftStatus": null
  }
]
```

Field semantics:

- `username`: stable employee identifier used by create/update shift requests.
- `name`: display name; falls back to `username` when the user projection has no name.
- `groupName`: employee home group from the user projection.
- `preferred`: `true` when `EmployeePreferWorkdayBoardService.getPreferredEmployeesBydate(date)` contains the username for the selected date.
- `alreadyScheduled`: `true` when the employee has any shift on the selected Vancouver business date.
- `existingShiftId`: lowest shift id for that employee on the selected date when `alreadyScheduled` is `true`; otherwise `null`.
- `existingShiftStatus`: status of `existingShiftId` when `alreadyScheduled` is `true`; otherwise `null`.

## Implementation Rules

- Keep this endpoint read-only. It exists to let the frontend render candidate state before the user submits a shift.
- Do not operate on the database directly from an agent run. If a future version needs a table, field, constraint, or data migration, stop and post the complete SQL in the issue for the user to execute.
- Prefer a dedicated DTO endpoint for this UI state instead of overloading `getBoardByDate` or returning full user/shift domain objects.
- Preserve `preferred` and `alreadyScheduled` as booleans with deterministic defaults. Missing preference data means `preferred: false`; no shift on the selected business day means `alreadyScheduled: false`.
- `alreadyScheduled` must be computed across the whole Vancouver local day so late UTC timestamps do not leak into the wrong scheduling day.
- If an employee has both `preferred: true` and `alreadyScheduled: true`, the backend returns both facts. The frontend owns display priority, with `Already scheduled` taking precedence over `Preferred`.
- Keep sorting stable by display name, case-insensitive, so the frontend list does not reorder unexpectedly between renders.
- If group filtering becomes required, document the new contract first and add targeted tests for blank, matching, and non-matching `groupName` behavior.

## Verification

Focused checks for this endpoint:

```bash
mvn test -Dtest=ShiftArrangementServiceTest,ShiftArrangementControllerCorsTest
mvn test
mvn -DskipTests package
git diff --check
```

The focused service test should cover at least:

- preferred only
- already scheduled only
- both preferred and already scheduled
- normal available
- display-name fallback to username
- Vancouver business-day boundaries

