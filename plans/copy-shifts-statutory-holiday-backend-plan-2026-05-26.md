# Copy Shifts Statutory Holiday Backend Plan

Date: 2026-05-26

Issue: MAN-18, "Fix copy shifts inserting assignments on statutory holidays"

Scope: backend plan only. No implementation until approved.

## Why This Is Split From Frontend

The earlier cross-stack plan existed because the bug spans the API boundary: frontend initiates copy, but backend creates the illegal records. For execution, separate plans are clearer. Backend developers need the authoritative validation rule, DTO contract, repository/service changes, and backend test matrix without frontend layout detail.

Frontend companion plan:

- `/Users/marktwain/Projects/OPBOA/plans/copy-shifts-statutory-holiday-frontend-plan-2026-05-26.md`

## Current Backend Context

Project stack:

- Spring Boot 3.2.3
- Maven
- Spring Web
- Spring Data JPA / Hibernate
- MySQL
- Java 17+

Relevant files:

- `src/main/java/ca/openbox/shift/controller/copy/ShiftPresetController.java`
- `src/main/java/ca/openbox/shift/service/copy/WeekScheduleService.java`
- `src/main/java/ca/openbox/shift/dto/PresetRequestDTO.java`
- `src/main/java/ca/openbox/shift/dto/PresetResultDTO.java`
- `src/main/java/ca/openbox/shift/repository/ShiftArrangementRepository.java`
- `src/main/java/ca/openbox/shift/repository/StatutoryHolidayRepository.java`
- `src/main/java/ca/openbox/shift/dataobject/StatutoryHolidayDO.java`
- `src/main/java/ca/openbox/shift/service/StatutoryHolidayService.java`

Current copy behavior in `WeekScheduleService.copyWeekSchedule(...)`:

1. Converts source and target week starts from `LocalDate` to `America/Vancouver` start-of-day and then UTC.
2. Validates target week is not earlier than source week.
3. Rejects the whole request if target week already has schedules for the group.
4. Loads source week shifts by group and source date range.
5. Generates copied target shifts by applying `offsetDays`.
6. Saves all generated shifts with `shiftArrangementRepository.saveAll(generatedShiftDOs)`.
7. Returns `created = generatedShiftDOs.size()`, `skipped = 0`, `overwritten = 0`.

Failure point: generated target shifts are saved without checking whether the generated target business date is a statutory holiday.

## Backend Responsibility

Backend must be authoritative for the statutory holiday invariant:

- No copied shift may be inserted when its target `America/Vancouver` business date is a statutory holiday.
- Frontend warnings are only UX and cannot protect the database.
- The validation must happen before `saveAll`.
- Future clients and scripts calling `POST /api/shift/preset` must receive the same protection.

Recommended behavior for this bugfix:

- Keep the copy request week-level.
- Keep target week conflict behavior unchanged.
- Treat statutory holiday candidates as per-shift skips, not as a hard failure for the whole request.
- Return structured skipped details so the frontend can explain partial success.

## API Contract

### Request

Keep endpoint and request body unchanged:

```http
POST /api/shift/preset
Content-Type: application/json
```

```json
{
  "groupName": "surrey",
  "srcWeekStart": "2026-05-17",
  "tgtWeekStart": "2026-05-24",
  "mode": "SKIP"
}
```

No new request field is needed.

### Response

Preserve existing response fields and add optional skipped detail.

`PresetResultDTO`:

```java
@Data
public class PresetResultDTO {
    private Integer created;
    private Integer skipped;
    private Integer overwritten;
    private List<PresetSkippedShiftDTO> skippedDetails;
}
```

New `PresetSkippedShiftDTO`:

```java
@Data
public class PresetSkippedShiftDTO {
    private String username;
    private String groupName;
    private LocalDate sourceDate;
    private LocalDate targetDate;
    private String reason;
    private String message;
}
```

Reason value for this bugfix:

```text
STATUTORY_HOLIDAY
```

Example partial-success response:

```json
{
  "created": 8,
  "skipped": 2,
  "overwritten": 0,
  "skippedDetails": [
    {
      "username": "alice",
      "groupName": "surrey",
      "sourceDate": "2026-05-18",
      "targetDate": "2026-05-25",
      "reason": "STATUTORY_HOLIDAY",
      "message": "Skipped because 2026-05-25 is a statutory holiday."
    }
  ]
}
```

If no holiday candidates are skipped, return `skipped = 0` and either `skippedDetails = []` or omit the field depending on existing serialization defaults. Prefer returning an empty list for simpler frontend logic.

### Error Behavior

Keep existing hard-fail behavior:

- `INVALID_SCHEDULE_RANGE` when the target week is earlier than the source week.
- `SHIFT_ALREADY_EXISTS` when the target week already has schedules under the current all-or-nothing target-week guard.

Do not turn statutory holiday skip into a full-request error. A target week can contain both valid copied shifts and skipped holiday shifts.

If `OVERWRITE` mode is implemented later, statutory holiday validation still wins: do not delete or insert holiday shifts.

## Service And Repository Plan

### `PresetSkippedShiftDTO`

Add a new DTO under:

```text
src/main/java/ca/openbox/shift/dto/PresetSkippedShiftDTO.java
```

Fields:

- `String username`
- `String groupName`
- `LocalDate sourceDate`
- `LocalDate targetDate`
- `String reason`
- `String message`

Use Lombok `@Data` to match existing DTO style.

### `PresetResultDTO`

Extend existing DTO:

- Keep `created`, `skipped`, `overwritten`.
- Add `List<PresetSkippedShiftDTO> skippedDetails`.

This is backward compatible for clients reading only the existing fields.

### Holiday lookup

Use the existing `StatutoryHolidayRepository.findByStatutoryDateBetween(LocalDate startDate, LocalDate endDate)` method.

Recommended injection:

```java
@Autowired
StatutoryHolidayRepository statutoryHolidayRepository;
```

Alternative: inject `StatutoryHolidayService` only if adding a range method there keeps service boundaries cleaner. Do not add a new database table or query outside the existing holiday model.

For each copy request:

1. Query holidays once for `tgtWeekStart` through `tgtWeekStart.plusDays(6)` inclusive.
2. Convert returned `StatutoryHolidayDO` rows to `Set<LocalDate>`.
3. Optionally keep a `Map<LocalDate, String>` of holiday names for clearer messages.

### `WeekScheduleService.copyWeekSchedule(...)`

Add a service-level constant:

```java
private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");
```

Recommended flow:

1. Build source and destination UTC range from `LocalDate` using `BUSINESS_ZONE`.
2. Run existing request and target-week conflict validation.
3. Load source week shifts.
4. Load target-week statutory holidays once.
5. Generate each target candidate.
6. Calculate:

```java
LocalDate sourceBusinessDate =
    shiftArrangementDO.getStart().withZoneSameInstant(BUSINESS_ZONE).toLocalDate();

LocalDate targetBusinessDate =
    generatedShiftDO.getStart().withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
```

7. If `targetBusinessDate` is a statutory holiday:
   - Add a `PresetSkippedShiftDTO`.
   - Do not add the candidate to `generatedShiftDOs`.
8. If not a statutory holiday:
   - Add the candidate to `generatedShiftDOs`.
9. Call `saveAll` only with non-holiday candidates.
10. Return:
    - `created = generatedShiftDOs.size()`
    - `skipped = skippedDetails.size()`
    - `overwritten = 0`
    - `skippedDetails = skippedDetails`

Implementation detail: if all generated candidates are skipped, either skip `saveAll` or call it with an empty list. Skipping the repository call is cleaner and easier to assert in unit tests.

## Edge Cases

### Partial-week source data

Only source shifts returned by the source-week query generate target candidates. Holiday checks apply to generated target candidates, not every date in the target week.

### Multiple holidays in target week

Load all target-week holidays and skip every generated candidate whose target business date is in the set. `skipped` counts skipped shifts, not distinct holiday dates.

### Source shifts on normal days copied onto target holidays

This is the reported bug. Backend skips those target candidates and creates valid candidates for non-holiday target dates.

### Source shifts already on source holidays

This plan does not clean or reinterpret existing source data. The rule checks only the generated target business date. If a historical source-holiday shift maps to a non-holiday target date, it can be copied.

### Timezone/date-only handling

- Request week fields are `LocalDate`.
- Holiday records are `LocalDate`.
- Shift start/end are `ZonedDateTime`.
- Convert generated target shift start to `America/Vancouver` before `toLocalDate()`.
- Do not compare UTC dates, formatted strings, or JVM default timezone dates for holiday decisions.

### Existing target-week schedules

Keep the existing whole-week duplicate guard. Do not combine this bugfix with conflict strategy redesign.

### Empty source week

Preserve current behavior: return `created = 0`, `skipped = 0`, `overwritten = 0`, `skippedDetails = []`.

## Backend Test Plan

Add or extend `WeekScheduleService` tests under `src/test/java`.

Recommended focused test cases:

1. Target week contains one statutory holiday and one generated candidate lands on it.
   - Mock source shifts.
   - Mock holiday repository returning the target holiday.
   - Verify `saveAll` receives only non-holiday candidates.
   - Assert `created`, `skipped`, `overwritten`, and `skippedDetails`.

2. Multiple holidays in target week.
   - Mock two target holiday dates.
   - Assert every candidate on either date is skipped.
   - Assert skipped count equals skipped shift count, not holiday-date count.

3. All generated shifts land on statutory holidays.
   - Assert `saveAll` is not called, or is called with an empty list only if that implementation is chosen deliberately.
   - Assert `created = 0` and `skippedDetails` contains all skipped shifts.

4. Timezone boundary.
   - Use a generated shift instant where UTC date and Vancouver local date differ.
   - Assert holiday comparison uses Vancouver business date.

5. Target week already has schedules.
   - Existing `DuplicateKeyException` behavior remains unchanged.
   - Assert holiday lookup/filtering does not bypass this validation.

6. Empty source week.
   - Assert no generated candidates, no skipped details, and stable zero counts.

Recommended command after implementation:

```bash
mvn test -Dtest=WeekScheduleServiceTest
```

If controller serialization tests already exist, add a `ShiftPresetController`/MockMvc test for `POST /api/shift/preset` response JSON containing:

- `created`
- `skipped`
- `overwritten`
- `skippedDetails[0].reason`
- `skippedDetails[0].targetDate`

No new CORS/security rule is expected because this endpoint is already a browser-facing `POST` under `/api/shift`.

## Database And Migration Statement

No database table, field, constraint, data migration, or SQL is needed.

Reason:

- `opb_statutory_holiday` already stores statutory holiday dates through `StatutoryHolidayDO`.
- `StatutoryHolidayRepository` already has `findByStatutoryDateBetween(...)`.
- `opb_shift_arrangement` already stores copied shifts.
- The fix is service-layer filtering before insert plus DTO response detail.

Because no DB change is needed, there is no SQL for the user to execute.
