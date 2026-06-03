# Delete Application Visibility Cross-Stack Plan

Date: 2026-05-28

Issue: MAN-25, optimize employee application cards so unavailable delete actions are not shown as clickable controls.

## Current Findings

Frontend:

- Employee application list is rendered by `app/applications/MyApplications.tsx`.
- Employee application card UI is rendered by `components/applications/ApplicationCardforE.tsx`.
- `ApplicationCardforE` always renders a clickable `Delete Application` button.
- `MyApplications.showDeleteModal` currently blocks only `application.status === "approved"` with an alert.
- `MyApplications.deleteCurrentApplication` currently shows a success alert before the delete request completes, does not await/catch the request, and does not refresh/remove the deleted card after confirmed backend success.
- Frontend type `model/LeaveApplication.ts` only has `status?: string`; there is no typed status union and no `canDelete`.

Backend:

- Delete endpoint is `DELETE /api/process/application/{applicationID}` in `LeaveApplicationController`.
- `LeaveApplicationService.deleteApplication` currently calls `leaveApplicationRepository.deleteById(applicationID)` directly.
- Therefore backend delete status validation is not currently present and must be added as the authoritative guard.
- Existing leave application statuses found in backend code are `pending`, `approved`, and `rejected`.
- `draft` and `cancelled` are not currently produced by the leave application backend, but they are included below as forward-compatible UI/API contract states because the issue explicitly calls them out.

## Status Delete Rules

Use one shared rule across frontend display and backend delete validation:

- Deletable: `pending`, `draft`.
- Not deletable: `approved`, `rejected`, `cancelled`.
- Unknown, missing, or empty status: not deletable by default.
- All status comparisons must normalize with `trim().toLowerCase()` before rule checks.

Rationale:

- `pending` and `draft` represent user-owned, not-final application states.
- `approved`, `rejected`, and `cancelled` are terminal or business-locked states and should not expose a destructive action.
- Defaulting unknown statuses to not deletable avoids accidentally exposing deletion for a future workflow state.
- Normalization prevents historical or inconsistent values such as `Pending` or ` pending ` from being incorrectly treated as locked while still keeping unknown values safe.

## Frontend Plan

### Component Hierarchy

- `app/applications/MyApplications.tsx`
  - owns fetching, delete confirmation modal state, delete request execution, and list refresh/removal.
  - passes each application to `ApplicationCardforE`.
- `components/applications/ApplicationCardforE.tsx`
  - owns card layout and status-specific delete area rendering.
  - receives `application` and `deleteApplication`.
  - derives `canDelete` from `application.canDelete` when present, otherwise from the local status helper.
- New helper, suggested location: `components/applications/applicationDeleteRules.ts` or `model/LeaveApplication.ts`
  - exports `type LeaveApplicationStatus = "pending" | "draft" | "approved" | "rejected" | "cancelled" | string`.
  - exports `normalizeLeaveApplicationStatus(status?: string | null): string`.
  - exports `isLeaveApplicationDeletable(status?: string | null): boolean`.
  - exports `resolveCanDelete(application: Pick<LeaveApplication, "status" | "canDelete">): boolean`.
  - exports `getDeleteUnavailableMessage(status?: string | null): string`.

### UI Rules

For `pending` and `draft`:

- Show `Delete Application` in the existing card action area.
- Keep it a danger/destructive secondary action, preferably link or outline red text, not a dominant primary button.
- Tapping opens the existing confirmation dialog.
- Confirmation still calls `DELETE /api/process/application/{id}`.

For `approved`, `rejected`, and `cancelled`:

- Do not render a clickable `Delete Application` button.
- Render lightweight explanatory text in the original action area or immediately near the status text.
- Suggested copy:
  - `approved`: `Approved applications can't be deleted.`
  - `rejected`: `Rejected applications can't be deleted.`
  - `cancelled`: `Cancelled applications can't be deleted.`
- Use small, subdued text, approximately 12-13px and neutral gray.
- Do not rely on tooltip-only explanation because mobile users cannot reliably access tooltip content.
- Do not show an error toast/alert for normal non-deletable cards because the UI should prevent the invalid click path.

For unknown status:

- Do not render a clickable delete action.
- Render `This application can't be deleted.`

### Mobile Layout

- Keep the action/status footer height stable so cards do not visually jump when mixed statuses appear.
- Replace the button with the lightweight message in the same footer region when not deletable.
- Allow message text to wrap on narrow screens.
- Avoid tooltip-only behavior and avoid disabled danger buttons on mobile.

### Frontend Data Modeling

Update `LeaveApplication` TypeScript interface/class to allow an optional API-provided flag:

```ts
canDelete?: boolean;
```

Frontend resolution order:

```ts
const resolveCanDelete = (application: Pick<LeaveApplication, "status" | "canDelete">) =>
  typeof application.canDelete === "boolean"
  ? application.canDelete
  : isLeaveApplicationDeletable(application.status);
```

This keeps the frontend backward compatible if backend and frontend are deployed at different times.

`ApplicationCardforE`, `MyApplications.showDeleteModal`, and any future delete entry point must use this same `resolveCanDelete(application)` helper. Do not keep separate ad hoc checks such as `status === "approved"` because rejected, cancelled, unknown, stale, or differently-cased statuses must be handled consistently.

### Delete Request Flow

`MyApplications.deleteCurrentApplication` must be changed from fire-and-forget behavior to an awaited request:

1. Store the selected application, not only the numeric ID, so the final guard can re-run `resolveCanDelete(selectedApplication)` before sending the request.
2. When the user confirms, set a deleting/loading state and call `await deleteApplication(id)`.
3. Show success feedback only after the backend confirms deletion.
4. Then close the modal and either remove the deleted application from `applicationList` or refetch the applicant's list.
5. If the backend returns `409 Conflict`, show a soft business message such as `This application can no longer be deleted.` and refetch the list because the status may have changed.
6. For other errors, show a generic failure message and keep the UI consistent; do not claim success.
7. Always clear the deleting/loading state in `finally`.

This flow is an implementation acceptance requirement. Hiding the button alone is not enough.

### Frontend Tasks

1. Add the shared delete-rule helper and focused tests for `pending`, `draft`, `approved`, `rejected`, `cancelled`, unknown, null, undefined, uppercase, and surrounding whitespace.
2. Add optional `canDelete?: boolean` to the frontend `LeaveApplication` model.
3. Update `ApplicationCardforE` to render either the delete action or the lightweight non-deletable message.
4. Update `MyApplications.showDeleteModal` to use `resolveCanDelete(application)` as a defensive UI guard, not only `approved`.
5. Update `MyApplications.deleteCurrentApplication` to await the delete request, show success only after backend success, handle `409 Conflict` with a soft business message, and refresh/remove the card after confirmed deletion.
6. Ensure the modal confirm button exposes a loading/disabled state while deletion is in flight to prevent duplicate delete requests.
7. Add focused render tests for:
   - pending card shows `Delete Application`.
   - draft card shows `Delete Application`.
   - approved/rejected/cancelled cards do not show clickable `Delete Application` and do show the correct message.
   - `Pending`, ` pending `, unknown, null, and undefined statuses follow the normalized/default-safe rules.
   - mobile-width rendering keeps the message visible and wrapped.
8. Add focused behavior tests for:
   - delete success waits for the API before success feedback and removes/refetches the card.
   - `409 Conflict` shows a soft non-deletable message and refreshes the list.
   - `showDeleteModal` blocks non-deletable statuses through `resolveCanDelete`.

## Backend Plan

### REST Contract

Existing endpoint stays:

- `DELETE /api/process/application/{applicationID}`

Delete behavior:

- If the application is `pending` or `draft`, delete it.
- If the application is `approved`, `rejected`, `cancelled`, unknown, empty, or null status, reject deletion.
- If the application does not exist, return a clear not-found response.
- Backend status checks must also normalize with `trim().toLowerCase()`.

Recommended error responses:

- `404 Not Found`: application ID does not exist.
- `409 Conflict`: application exists but current status is not deletable.

Suggested conflict reason:

```text
Application status does not support deletion
```

### Response DTO Contract

Add a response DTO instead of continuing to expose only the domain entity directly for frontend-facing list/history payloads:

```java
public class LeaveApplicationResponseDTO {
    private Integer id;
    private String applicant;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private String leaveType;
    private ZonedDateTime submitTime;
    private String currentHandler;
    private String status;
    private String rejectReason;
    private String reason;
    private String note;
    private boolean canDelete;
}
```

Initial rollout can be backward compatible because existing fields remain unchanged and `canDelete` is an additive field.

Compatibility requirement:

- Preserve existing JSON field names and shapes used by both employee and management pages.
- `GET /api/process/application` and `GET /api/process/application/history` are shared surfaces; adding `canDelete` must not rename, remove, or change the type of existing `LeaveApplication` fields.
- If introducing `LeaveApplicationResponseDTO` causes broad churn, an acceptable lower-risk implementation is adding a transient/read-only `canDelete` response property through the existing mapping layer, as long as the JSON contract remains additive.

Apply this DTO to:

- `GET /api/process/application`
- `GET /api/process/application/history`
- `PUT /api/process/application/leave-application` response if practical, so newly created pending applications immediately include `canDelete: true`.

The frontend should still keep the local status helper fallback because older backend responses may not include `canDelete`.

### Service and Repository Layers

Recommended service additions:

- Add constants in `LeaveApplicationService`:
  - `PENDING_STATUS = "pending"`
  - `DRAFT_STATUS = "draft"`
  - `APPROVED_STATUS = "approved"`
  - `REJECTED_STATUS = "rejected"`
  - `CANCELLED_STATUS = "cancelled"`
  - `DELETABLE_STATUSES = Set.of(PENDING_STATUS, DRAFT_STATUS)`
- Add method:
  - `boolean canDeleteApplicationStatus(String status)`
  - This method must normalize status with null-safe `trim().toLowerCase()` and return false for blank or unknown values.
- Update `deleteApplication(Integer applicationID)`:
  1. Load `LeaveApplicationDO` by ID.
  2. If null, throw `ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")`.
  3. If status is not deletable, throw `ResponseStatusException(HttpStatus.CONFLICT, "Application status does not support deletion")`.
  4. Delete by ID.
- Add mapper method for `LeaveApplicationResponseDTO` that sets `canDelete` by calling the same service rule.

Repository impact:

- Existing `getLeaveApplicationDOById(Integer id)` and `deleteById(Integer id)` are enough.
- No new query is required.

### Backend Tasks

1. Add normalized status constants and `canDeleteApplicationStatus(String status)` in `LeaveApplicationService` or a small focused helper if the service is getting crowded.
2. Change `deleteApplication(Integer applicationID)` so status validation is mandatory before delete. This is not optional and must be the final business-rule guard.
3. Add additive `canDelete` metadata to list/history responses without breaking existing field names or response shapes.
4. Keep repository usage simple; use existing lookup/delete methods unless implementation reveals a missing null-safe lookup path.
5. Return `409 Conflict` for non-deletable existing applications and `404 Not Found` for missing IDs.

### Backend Tests

Add focused service/controller tests:

- `deleteApplication` deletes `pending`.
- `deleteApplication` deletes `draft` if such a record exists or can be constructed in tests.
- `deleteApplication` rejects `approved`, `rejected`, `cancelled`, unknown, and null status with `409 Conflict`.
- `deleteApplication` treats `Pending` and ` pending ` as deletable after normalization.
- `deleteApplication` rejects missing application with `404 Not Found`.
- Application list/history DTOs include `canDelete: true` for `pending`/`draft` and `false` for terminal states.

## Frontend/Backend Interaction Contract

Frontend display should prefer backend `canDelete` when available:

- `canDelete === true`: render clickable delete action.
- `canDelete === false`: render non-clickable lightweight explanation.
- `canDelete` absent: use frontend helper against `status`.

Frontend delete execution should still treat backend as source of truth:

- If the frontend thinks an application is deletable but `DELETE` returns `409 Conflict`, the frontend should show the non-deletable business message and refresh the list.
- If the frontend thinks an application is not deletable, it should not open the confirmation modal from any UI path.

Backend remains authoritative:

- Even if the frontend renders a delete action due to stale state, direct API calls, cached UI, or a race where status changed after fetch, backend must reject non-deletable status on `DELETE`.

This contract avoids a hard deployment dependency while still moving the source of truth toward backend-provided capability metadata.

## Database Impact

No table, field, index, constraint, or data migration is required for this change.

No SQL is needed.

`canDelete` is a derived response field from the existing `status`; it should not be persisted.

## Verification Plan

Frontend:

```bash
TMPDIR=/Users/marktwain/Projects/OPBOA/.jest-tmp npx jest --runInBand --watchAll=false components/__tests__/ApplicationCardDeleteVisibility-test.js components/__tests__/ApplicationDeleteRules-test.js
```

Backend:

```bash
mvn test -Dtest='*LeaveApplication*'
```

Manual checks:

- Pending application card shows `Delete Application`, opens confirmation, and disappears after successful deletion.
- Success feedback appears only after the delete API resolves successfully.
- A backend `409 Conflict` displays a soft business message and refreshes the application list.
- Approved/rejected/cancelled cards show no clickable delete action and display the lightweight message.
- `Pending` and ` pending ` are treated as deletable; null/unknown statuses are treated as not deletable.
- Mobile-width card keeps the status and explanation readable without relying on tooltips.
- Direct `DELETE` against approved/rejected/cancelled application returns conflict and does not delete the row.
