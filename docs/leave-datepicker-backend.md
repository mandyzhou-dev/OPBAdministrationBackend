# Leave Application DatePicker Backend Notes

Date: 2026-05-27
Issue: MAN-19

## Scope

The DatePicker work keeps the existing leave submission model and adds backend support for frontend date availability checks. The frontend now owns the DatePicker UI, while the backend remains authoritative for whether submitted leave dates are valid.

Time input is intentionally unchanged for this backend scope. One-day leave continues to submit the existing `start` and `end` zoned datetimes derived from the frontend's manual `HHmm-HHmm` range input. There are no new time fields, no TimePicker contract, and no start/end time split on the backend.

## Sick Leave Date Availability Endpoint

Route, including the servlet context path:

```http
GET /api/process/application/leave-date-availability?applicant=<username>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>
```

Controller path:

```http
GET /process/application/leave-date-availability
```

Request parameters:

| Name | Required | Format | Meaning |
| --- | --- | --- | --- |
| `applicant` | Yes | username string | Employee whose schedule should be checked. Blank values are rejected. |
| `from` | Yes | `YYYY-MM-DD` | Inclusive Vancouver business date range start. |
| `to` | Yes | `YYYY-MM-DD` | Inclusive Vancouver business date range end. |

Validation:

- `from` and `to` are parsed as `LocalDate`.
- `to` cannot be before `from`.
- Availability ranges are capped at 120 inclusive days.
- Invalid requests return `400 BAD_REQUEST`.

Response:

```json
{
  "applicant": "employee1",
  "from": "2026-05-27",
  "to": "2026-05-28",
  "businessZone": "America/Vancouver",
  "dates": [
    {
      "date": "2026-05-27",
      "scheduled": true,
      "shiftIds": [123]
    },
    {
      "date": "2026-05-28",
      "scheduled": false,
      "shiftIds": []
    }
  ]
}
```

Implementation boundary:

- `LeaveApplicationController` parses request params and delegates.
- `LeaveApplicationService.getLeaveDateAvailability(...)` owns validation, range expansion, shift lookup, and DTO construction.
- `LeaveDateAvailabilityDTO` and `LeaveDateAvailabilityDateDTO` are response-only DTOs.
- Shift data is read from `ShiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(...)`.

## Submit-Time Authority

The frontend DatePicker is UX only. `LeaveApplicationService.addLeaveApplication(...)` validates dates before saving:

- `start` and `end` are required.
- Submitted zoned datetimes are converted to `America/Vancouver` local dates.
- Any selected Vancouver business date before today's Vancouver date is rejected with `400 BAD_REQUEST`.
- For `leaveType = SICK`, every selected Vancouver business date must have at least one shift for the applicant.
- A sick leave date range is checked inclusively from start business date through end business date.
- Time values are not validated against the scheduled shift start/end times.

Current error messages:

- `Leave start and end are required`
- `Date range is required`
- `Date range end cannot be before start`
- `Leave date cannot be before today`
- `Applicant is required`
- `Sick leave requires an existing scheduled shift for every selected date`

## Vancouver Business Date Handling

Business date means `America/Vancouver`, not UTC and not the database session timezone.

Implementation rules:

- Use `ZoneId.of("America/Vancouver")`.
- Convert submitted `ZonedDateTime` values through `withZoneSameInstant(BUSINESS_ZONE).toLocalDate()`.
- For availability lookup, convert each requested `LocalDate` to Vancouver day boundaries.
- Query shifts for the whole range once, from `from.atStartOfDay(BUSINESS_ZONE)` through `to.plusDays(1).atStartOfDay(BUSINESS_ZONE).minusNanos(1)`.
- Group returned shifts by their Vancouver local date.

This matches existing backend practice for scheduling, copy schedule checks, statistics, KPI input, and paid sick leave quota.

## Database Conclusion

This work does not require a database schema change, data migration, new table, new column, new constraint, or production data update.

The implementation reuses:

- `opb_leave_application`
- `opb_shift_arrangement`
- existing leave application DTO/entity persistence
- existing shift arrangement repository reads

If a future leave-date requirement needs persistent availability state or proof-document storage, stop and post complete SQL in the issue for user execution before changing the database.

## Verification Commands

Targeted tests for this backend feature:

```bash
mvn test -Dtest=LeaveApplicationServiceDateAvailabilityTest,LeaveApplicationControllerAvailabilityTest
```

Full backend test/build commands:

```bash
mvn test
mvn clean package
```

Test date note:

- Backend tests use fixed `2026-05-27` / `2026-05-28` dates and a fixed `Clock` only as deterministic fixtures.
- Production logic does not hard-code those dates. It computes today's date with `Clock.system(ZoneId.of("America/Vancouver"))` and validates submitted `ZonedDateTime` values after converting them to Vancouver `LocalDate`.
- When adding or reviewing tests, keep fixed dates inside test data and avoid introducing fixed business dates into controller or service production paths.

The 2026-05-27 implementation was verified with:

```bash
mvn test -Dtest=LeaveApplicationServiceDateAvailabilityTest,LeaveApplicationControllerAvailabilityTest
mvn test
mvn clean package
```
