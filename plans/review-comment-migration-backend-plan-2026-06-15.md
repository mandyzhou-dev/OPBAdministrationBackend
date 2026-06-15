# Review Comment Migration Backend Plan

Issue: MAN-32

Scope: planning only. Do not implement until the user approves.

## Goal

Migrate the leave-application decision comment from the rejection-only
`rejectReason` semantics to a neutral `reviewComment` semantics.

After migration:

- Approve may save an optional review comment.
- Decline must save a nonblank review comment.
- Existing rejected records keep their old rejection text as review comment.
- The backend response uses `reviewComment`, not `rejectReason`.
- Direct database changes are user-executed only.
- Only `reviewComment` may be trimmed for blank validation. Applicant,
  current-handler, and username strings must preserve their existing values,
  including trailing spaces such as `Harsimranjit Kaur `.

## Current Backend State

Relevant files:

- `src/main/java/ca/openbox/process/dataobject/LeaveApplicationDO.java`
- `src/main/java/ca/openbox/process/entities/LeaveApplication.java`
- `src/main/java/ca/openbox/process/controller/LeaveApplicationController.java`
- `src/main/java/ca/openbox/process/service/LeaveApplicationService.java`
- `src/main/java/ca/openbox/process/repository/LeaveApplicationRepository.java`

Current data meanings:

- `reason`: employee-submitted application reason/comment.
- `rejectReason`: manager rejection reason, currently written only by Decline.
- `note`: manager/admin history note, editable after the application decision.

Current endpoint behavior:

- `POST /api/process/application/{applicationID}/permit` accepts no body and drops any modal text.
- `POST /api/process/application/{applicationID}/reject` accepts a plain text body and writes it to `rejectReason`.
- Read endpoints return `LeaveApplication` objects with `rejectReason`.

## Target Contract

Use one neutral decision-comment field:

```json
{
  "reviewComment": "Approved, but please submit the handoff note by Friday."
}
```

Decision behavior:

- Approve: `reviewComment` is optional. Blank input should persist as `null` or empty according to existing repository conventions; prefer `null` if simple.
- Decline: `reviewComment` is required. Backend should reject blank comments with `400 Bad Request`.
- `reason` remains the employee request reason.
- `note` remains the editable post-decision history note.

Read behavior:

- `GET /api/process/application`
- `GET /api/process/application/history`
- `GET /api/process/application?handler=...`
- `GET /api/process/application?applicant=...`

All should return `reviewComment` in the `LeaveApplication` response. They should no longer expose `rejectReason` after the frontend is migrated.

## Database Change For User Execution

Preferred direct migration, if the deployed MySQL version supports `RENAME COLUMN`:

```sql
-- Pre-check
SHOW COLUMNS FROM opb_leave_application LIKE 'reject_reason';
SHOW COLUMNS FROM opb_leave_application LIKE 'review_comment';

-- Migration
ALTER TABLE opb_leave_application
  RENAME COLUMN reject_reason TO review_comment;

-- The review modal uses a textarea and approval conditions may be longer than
-- the old rejection reason. Store the migrated field as TEXT.
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

Rollback if needed:

```sql
ALTER TABLE opb_leave_application
  RENAME COLUMN review_comment TO reject_reason;

-- Optional rollback type narrowing only if all values are within 255 chars.
-- Otherwise keep TEXT or choose a larger bounded type.
-- ALTER TABLE opb_leave_application
--   MODIFY COLUMN reject_reason VARCHAR(255) NULL;
```

Fallback for older MySQL versions that do not support `RENAME COLUMN`:

```sql
-- First inspect the current type.
SHOW COLUMNS FROM opb_leave_application LIKE 'reject_reason';

-- Single-step fallback: rename and widen the column.
ALTER TABLE opb_leave_application
  CHANGE COLUMN reject_reason review_comment TEXT NULL;
```

No data-copy SQL is needed for the direct rename because the column data is
preserved. Execute this only in the coordinated migration window, because old
code expects `reject_reason` and migrated code expects `review_comment`.

## Release Sequence

This migration is not a rolling-deploy-safe change unless a temporary alias is
added. Plan for a coordinated release window:

1. Stop or pause the old backend that still expects `reject_reason`.
2. User executes the database migration SQL above.
3. Deploy the migrated backend that expects `review_comment`.
4. Deploy the migrated frontend that sends/reads `reviewComment`.
5. Run the API and UI verification checks.

If zero-downtime or mixed old/new frontend support is required, add a temporary
compatibility layer before approval. That is a separate compatibility decision,
not part of the clean semantic migration.

## Backend Implementation Steps

1. Rename persistence field:
   - In `LeaveApplicationDO`, replace `rejectReason` with `reviewComment`.
   - Add `@Column(name = "review_comment")` explicitly. Do not rely on implicit
     camelCase-to-snake_case naming for this migrated field.

2. Rename domain/response field:
   - In `LeaveApplication`, replace `rejectReason` with `reviewComment`.
   - Update `fromDO(...)` and `toDO()` mappings.
   - Keep `reason` and `note` unchanged.

3. Add a decision request DTO:
   - Create a small DTO such as `ReviewDecisionDTO`.
   - Field: `private String reviewComment;`.

4. Update approve endpoint:
   - Change `permit(...)` to accept an optional request body using
     `@RequestBody(required = false) ReviewDecisionDTO reviewDecisionDTO`.
   - Delegate to `leaveApplicationService.permitApplication(applicationID, reviewComment)`.
   - Approve should allow blank or missing `reviewComment`.

5. Update decline endpoint:
   - Change `reject(...)` to accept the same DTO.
   - Validate nonblank `reviewComment` after trimming. A string containing only
     spaces should return `400 Bad Request`.
   - Delegate to `leaveApplicationService.rejectApplication(applicationID, reviewComment)`.

6. Update service methods:
   - `permitApplication(id, reviewComment)` sets status to `approved`, sets current handler to applicant, saves normalized `reviewComment`.
   - `rejectApplication(id, reviewComment)` sets status to `rejected`, sets current handler to applicant, saves required normalized `reviewComment`.
   - Return or persist consistently with existing method style; no new cross-service behavior is required.
   - If `getLeaveApplicationDOById(...)` returns null, throw `404 Not Found`
     instead of allowing a null pointer exception.
   - When setting `currentHandler` back to the applicant, use
     `leaveApplicationDO.getApplicant()` exactly. Do not trim or normalize it.

7. Preserve username/applicant compatibility:
   - Do not trim `LeaveApplication.applicant`.
   - Do not trim `LeaveApplicationDO.applicant`.
   - Do not trim `currentHandler`.
   - Do not change the existing sick-proof username compatibility behavior.
   - The only trim introduced by this work should be for `reviewComment` blank
     handling.

8. Update tests:
   - Approve with comment persists `reviewComment`.
   - Approve without comment succeeds.
   - Decline with comment persists `reviewComment`.
   - Decline without comment returns `400`.
   - Decline with only spaces returns `400`.
   - Missing application returns `404` for approve and decline.
   - History/read DTO mapping returns `reviewComment`.
   - Existing proof/status/history tests should be updated from `rejectReason` to `reviewComment`.
   - Controller MockMvc tests cover JSON body and no-body approve.
   - Regression test: approving/rejecting an application for applicant
     `Harsimranjit Kaur ` preserves the trailing space and writes
     `currentHandler` from the original applicant value.

## API Compatibility Notes

This is an intentional semantic migration. The frontend should migrate in the
same release from `rejectReason` to `reviewComment`.

If backward compatibility with older frontend builds is required, add a
temporary response alias for `rejectReason` only during a transition window.
That is not recommended unless product explicitly needs rolling deployments.

The old text/plain reject contract is intentionally replaced by JSON DTO input.
Because this is not backward compatible, do not deploy the migrated backend
alone while the old frontend is still active.

## Notification Scope

This plan only guarantees review-comment persistence and display through Time
Off Requests, My Applications, and History. It does not add approve/reject email
or push notifications. If product expects employees to be proactively notified
of approval conditions, that notification behavior should be planned explicitly
before implementation.

## Verification

Suggested backend verification after implementation and user-executed DB rename:

```bash
mvn test -Dtest=LeaveApplicationControllerTest
mvn test -Dtest=LeaveApplicationServiceHistoryTest,LeaveApplicationCanDeleteTest
mvn test
mvn package -DskipTests
```

Manual API checks:

- Approve with JSON body `{ "reviewComment": "Approved with handoff required" }`.
- Approve with no body.
- Decline with JSON body `{ "reviewComment": "Insufficient coverage" }`.
- Decline with blank comment should return `400`.
- Fetch Time Off Requests, My Applications, and History and confirm `reviewComment` is returned.
