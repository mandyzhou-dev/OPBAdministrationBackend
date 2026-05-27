# Leave Application DatePicker Cross-Stack Plan

Date: 2026-05-27
Issue: MAN-19

This is the same cross-stack execution plan saved in the backend project so Backend_Dev can work from the local `plans` directory. The frontend copy is:

`/Users/marktwain/Projects/OPBOA/plans/leave-application-datepicker-cross-stack-plan-2026-05-27.md`

## Goal

Change the employee leave application form from manual date text input to DatePicker-based selection. Dates before the current Vancouver business date must be unavailable. For `SICK` leave, the user may select only dates where the applicant already has a scheduled shift.

Frontend time input direction: time remains required HR context for one-day leave and should stay as the existing single manual `HHmm-HHmm` range input. Do not upgrade to TimePicker and do not split it into separate start/end controls. Keep the visible helper prompt `Format: HHmm-HHmm`. This does not require backend DTO, entity, repository, schema, or migration changes.

No database schema, field, constraint, or data migration is required. This plan uses existing leave application and shift arrangement tables.

## Backend Scope

### New Read Endpoint

Add a read-only endpoint under the leave application boundary:

```http
GET /api/process/application/leave-date-availability?applicant=<username>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>
```

Response:

```json
{
  "applicant": "employee1",
  "from": "2026-05-27",
  "to": "2026-06-30",
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

Controller boundary:

- Add `GET /application/leave-date-availability` to `LeaveApplicationController`.
- Parse `from` and `to` as `LocalDate`.
- Delegate all business logic to `LeaveApplicationService`.

Service boundary:

- Add `getLeaveDateAvailability(String applicant, LocalDate from, LocalDate to)`.
- Validate `applicant` is nonblank.
- Reject very large ranges. Recommended max: 120 days, returning `400 BAD_REQUEST`.
- Convert each `LocalDate` to `America/Vancouver` day start and next day exclusive.
- Query shifts for the applicant between range start inclusive and range end exclusive.
- Group by Vancouver `LocalDate`.
- Return one DTO item per date in the requested inclusive range.

Repository boundary:

- Reuse `ShiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(...)`.
- Query once for the whole range.
- Because the existing derived repository method uses `Between`, pass `to.plusDays(1).atStartOfDay(zone).minusNanos(1)` if retaining inclusive end semantics.

DTOs to add under `ca.openbox.process.dto`:

- `LeaveDateAvailabilityDTO`
- `LeaveDateAvailabilityDateDTO`

No JPA entity changes are needed.

### Submit-Time Validation

Add authoritative validation in `LeaveApplicationService` before save:

- Reject any leave application where any selected Vancouver business date is before today's Vancouver date.
- If `leaveType` is `SICK`, require every selected Vancouver business date in the requested leave range to have at least one shift for `applicant`.
- For one-day partial leave, check the date of `start`.
- For multi-day range leave, check every inclusive local date from `start` to `end`.
- Return `400 BAD_REQUEST` with clear messages:
  - `Leave date cannot be before today`
  - `Sick leave requires an existing scheduled shift for every selected date`

Use `ZoneId.of("America/Vancouver")`, consistent with existing shift, KPI, copy schedule, and paid sick leave logic.

## Frontend Contract Summary

Frontend should reuse Ant Design `DatePicker` from `components/shift/SelectShiftForm.tsx` and `app/applications/MyPreferShift.tsx`, not MUI or `react-datepicker`.

Add request method:

```ts
getLeaveDateAvailability(applicant: string, from: string, to: string): Promise<LeaveDateAvailability>
```

Request dates are `YYYY-MM-DD`; submit payload keeps existing ISO zoned datetime strings for `start` and `end`.

For one-day leave, the frontend should build those ISO values from:

- selected DatePicker date
- the existing manual `HHmm-HHmm` time range input

Backend validation remains date-authoritative: past-date checks and sick-leave scheduled-date checks use Vancouver `LocalDate`; time is not validated against scheduled shift start/end.

## Date and Timezone Rules

- Availability endpoint request dates are date-only `YYYY-MM-DD` in `America/Vancouver`.
- Availability response dates are date-only `YYYY-MM-DD` in `America/Vancouver`.
- Leave submit keeps existing ISO zoned datetimes for `start` and `end`.
- Backend converts submitted `ZonedDateTime` values to Vancouver `LocalDate` for validation.
- Today means current date in `America/Vancouver`, not UTC.

## Backend Task Decomposition

1. Add `LeaveDateAvailabilityDTO` and `LeaveDateAvailabilityDateDTO`.
2. Add `LeaveApplicationService.getLeaveDateAvailability(...)`.
3. Add validation helpers:
   - `toBusinessDate(ZonedDateTime)`
   - `getBusinessDatesInclusive(start, end)`
   - `assertNotPast(...)`
   - `assertScheduledForSickLeave(...)`
4. Update `addLeaveApplication(...)` to call validation before save.
5. Add `GET /application/leave-date-availability` to `LeaveApplicationController`.
6. Add focused unit tests for availability and submit validation.
7. Add or update controller/CORS tests if the project has coverage for process endpoints.

## Acceptance Criteria

- Backend returns schedule availability for one applicant over a date range.
- Backend rejects invalid past leave dates even if the frontend is bypassed.
- Backend rejects sick leave dates without scheduled shifts even if the frontend is bypassed.
- Backend accepts personal leave for today or future dates.
- Backend accepts sick leave only when every selected Vancouver business date has an applicant shift.
- Existing leave submit response shape remains compatible.
- No database migration or direct database operation is needed.

## Suggested Backend Tests

- Availability endpoint returns `scheduled: true` with `shiftIds` for a date with an applicant shift.
- Availability endpoint returns `scheduled: false` for a date without an applicant shift.
- Availability endpoint uses Vancouver local date boundaries for shifts near UTC day boundaries.
- Submitting non-sick leave for yesterday returns `400`.
- Submitting personal leave for today succeeds.
- Submitting sick leave for a scheduled date succeeds.
- Submitting sick leave for an unscheduled date returns `400`.
- Submitting sick leave range with one unscheduled date returns `400`.

## Implementation Notes

- Do not use `GET /api/shift/shiftarrangement/candidatesByDate` directly for the leave form. It returns all role candidates for shift assignment UI and is not shaped for one applicant's leave eligibility or submit validation.
- Keep the new API contract additive.
- The Time UI remains frontend-only and keeps the existing `HHmm-HHmm` range format. Do not add backend fields for it unless a future requirement changes the persistence model.
- If implementation discovers a required database change, stop and post the reason plus complete SQL in the issue for user execution before proceeding.
