# Leave Application DatePicker Cross-Stack Architecture

Date: 2026-05-27
Issue: MAN-19

## Final Product Scope

The leave application date fields moved from manual date entry to picker-based selection.

- One-day leave uses Ant Design `DatePicker`.
- Range leave uses Ant Design `DatePicker.RangePicker`.
- The one-day time field stays as the existing manual `HHmm-HHmm` range input.
- Time label is `Time`; helper text is `Format: HHmm-HHmm`.
- Do not introduce a TimePicker, split start/end time inputs, or validate time against scheduled shift time unless a future approved requirement changes scope.

The sick leave proof upload discussion was split into a separate issue and is not part of this DatePicker scope.

## Requirement Evolution

1. Initial requirement: replace leave-date manual input with a DatePicker and block dates before today.
2. Sick leave rule added: sick leave can only be requested for dates where the employee already has a scheduled shift.
3. Cross-stack rule settled: frontend DatePicker disables invalid dates for UX, while backend submit validation remains authoritative.
4. UI follow-up settled: sick leave availability guidance is field helper text under the date picker, not a standalone row/card.
5. Time direction settled: keep the existing manual time range field and move format guidance into helper text.
6. Test-date concern clarified: fixed dates such as `2026-05-27` in Jest are fixtures for stable tests, not production business dates.

## Business Date Rules

The canonical business calendar is `America/Vancouver`.

- Vancouver today is selectable.
- Dates before Vancouver today are disabled in the frontend and rejected by the backend.
- Normal leave applies only the past-date rule.
- Sick leave applies the past-date rule plus scheduled-shift availability for every selected business date.
- Multi-day sick leave ranges are inclusive from selected start business date through selected end business date.

Do not use browser-local or UTC-only date comparisons for eligibility decisions.

## Frontend And Backend Date Formats

Availability request and response values are date-only Vancouver business dates:

```text
YYYY-MM-DD
```

Leave submission remains compatible with the existing backend payload:

```text
start: ISO zoned datetime
end: ISO zoned datetime
```

The backend converts submitted zoned datetimes to Vancouver `LocalDate` for validation.

## Availability API Contract

Frontend calls:

```http
GET /api/process/application/leave-date-availability?applicant=<username>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>
```

Controller route under servlet context:

```http
GET /process/application/leave-date-availability
```

Response:

```json
{
  "applicant": "employee1",
  "from": "2026-05-27",
  "to": "2026-05-28",
  "businessZone": "America/Vancouver",
  "dates": [
    { "date": "2026-05-27", "scheduled": true, "shiftIds": [123] },
    { "date": "2026-05-28", "scheduled": false, "shiftIds": [] }
  ]
}
```

Contract rules:

- `from` and `to` are inclusive.
- Backend rejects invalid ranges and ranges over 120 days.
- Frontend may prefetch a 90-day sick leave window, then refetch the exact selected range before submit if availability is missing or stale.
- Missing or `scheduled: false` dates are not selectable for sick leave.

## Backend Authority

The DatePicker is a convenience layer only. Backend submit validation must still reject:

- missing start/end values
- end business date before start business date
- any selected Vancouver business date before Vancouver today
- sick leave where any selected Vancouver business date has no applicant shift

This work required no database schema change, migration, new table, new column, new constraint, or data repair. Existing leave application and shift arrangement data are sufficient.

## Not Adopted

The team discussed TimePicker and separate start/end time controls, but did not adopt them for MAN-19.

Reason:

- The approved requirement was a date selection change, not a time-input redesign.
- Employees already know the existing `HHmm-HHmm` input.
- Adding TimePicker or split fields would expand validation, mobile layout, payload mapping, tests, and support burden without solving the DatePicker requirement.

## Acceptance Checklist

- One-day leave date uses `DatePicker`.
- Range leave dates use `RangePicker`.
- Vancouver today is allowed; dates before Vancouver today are blocked.
- Normal leave does not require schedule availability.
- Sick leave disables dates without an applicant shift.
- Sick leave submit revalidates selected date or inclusive date range.
- Backend rejects bypassed invalid submissions.
- Availability endpoint uses `YYYY-MM-DD` and returns `businessZone: "America/Vancouver"`.
- Submit payload remains existing ISO zoned `start` / `end`.
- Time remains manual with `Time` label and `Format: HHmm-HHmm` helper.
- No DB change is required.

## Reusable References

- Backend details: `docs/leave-datepicker-backend.md`
- Backend skill: `.codex/skills/opb-leave-datepicker-backend-workflow/SKILL.md`
- Frontend details: `/Users/marktwain/Projects/OPBOA/docs/feature_spec/leave-application-datepicker-frontend-knowledge.md`
- Frontend skill: `/Users/marktwain/Projects/OPBOA/.codex/skills/opboa-leave-datepicker-workflow/SKILL.md`
