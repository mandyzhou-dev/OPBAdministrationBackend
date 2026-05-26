# Fullstack Architect Reusable Notes - 2026-05-20

These notes capture reusable cross-stack lessons from the Application History employee filter work and the Select Shift Form candidate availability work. They are for future planning and coordination; they do not change feature scope.

## Planning Gate

- For unclear product requests, create a frontend/backend plan first and wait for explicit user confirmation before implementation.
- Keep the plan under the project `plans/` directory and make the frontend/backend API contract concrete enough that each side can work independently.
- If the user asks to discuss UI details first, do not code. Capture the options, trade-offs, and recommended decision, then wait for confirmation.
- Treat user UI sketches as candidate solutions, not automatically as requirements. First restate the underlying job-to-be-done, then confirm the recommended interaction before planning implementation.
- For front/back-separated work, the Fullstack Architect deliverable is the executable plan. Developers should not start until the plan records the approved product decision, API contract, DTO names, field semantics, file-level touch points, and verification checklist.

## Frontend Scope Control

- Small UI requests must stay small. Do not use a filter, button, or text truncation change as a reason to redesign the whole page.
- For Application History cards, preserve the compact summary pattern: list cards show scan-friendly information, while full details stay in the record detail modal.
- Avoid duplicate detail entrances. If the card has a single details icon, do not add another `View record`, `Show more`, or expanded-detail control unless the user explicitly approves it.
- Mobile behavior is part of the plan: controls need tappable hit areas, responsive widths, text truncation that does not break layout, and no hover-only interaction.
- For status-heavy rows, record the state priority in the plan so frontend behavior and labels stay deterministic. Select Shift Form uses `Already scheduled > Selected > Preferred > Normal available`.

## Backend/API Boundaries

- Do not overload an existing endpoint when the existing semantics differ. In this case, employee My Applications can include pending records, while admin History must mean non-pending history records.
- Keep authorization in a service/policy layer rather than scattering role checks through controllers or repository methods.
- Name frontend types and backend contracts around the domain capability, such as `application history` and `employee visibility`, rather than hard-coding role names into every component or DTO.
- Preserve extension points for future roles. The current implementation may be Manager-only, but the API should be able to support narrower visibility scopes later without a second rewrite.
- Contract first is mandatory for front/back-separated changes. The plan must define route, method, query params, DTO shape, boolean/null defaults, existing endpoint compatibility, and empty-result behavior before implementation.
- Prefer additive read DTO endpoints when the UI needs merged state from multiple backend concepts. For Select Shift Form, a dedicated `GET /api/shift/shiftarrangement/candidatesByDate` contract is clearer than making the frontend merge preference usernames with shift queries.
- Boolean DTO fields exposed to the frontend should be non-null in JSON. For scheduling candidates, `preferred` means the employee prefers the selected business date; `alreadyScheduled` means the employee already has any shift on that selected business date and must not be selectable.

## Database Rule

- Agents must not directly apply schema or data changes for this project.
- If a feature needs a table, column, constraint, index, or migration, the plan/comment must include complete SQL and state that the user must execute it.
- If no database change is needed, say that explicitly in the plan so Backend_Dev and Project_Manager can verify the boundary.
- This rule applies before implementation, during implementation, and during verification. If a later discovery shows the schema cannot support the approved contract, stop and post the SQL for user execution instead of changing the database directly.

## Verification Notes

- Frontend changes should include focused Jest coverage when behavior changes, plus `git diff --check`.
- `npx tsc --noEmit` may currently be blocked by unrelated existing TypeScript errors. When that happens, report the exact unrelated files and confirm the touched History files are not in the error list.
- Backend changes should include focused service/controller/repository tests when API behavior changes, plus the Maven test command that was run.

## Select Shift Form Candidate Availability

- Confirm the product goal before planning: manager needs a clear scheduling hint for employees who prefer the selected date, and a stronger disabled signal for employees who already have a shift.
- Do not hard-code a user's first visual draft as the only UI option. The approved UI for this issue replaced an isolated green dot with explicit `Prefers this day` and `Already scheduled` labels, with legend/tooltip only as support.
- Plan the backend DTO before frontend implementation:

```ts
interface ShiftCandidate {
  username: string;
  name: string;
  groupName?: string | null;
  preferred: boolean;
  alreadyScheduled: boolean;
  existingShiftId?: number | null;
  existingShiftStatus?: string | null;
}
```

- Keep `GET /api/shift/shiftboard/getBoardByDate` and `PUT /api/shift/shiftarrangement/batchCreateByDate` compatible; the candidates endpoint is additive and read-only.
- The frontend must block already scheduled employees both when changing row selection and immediately before submit.
- Mobile layout is part of acceptance: labels need to wrap or stack cleanly, rows need stable tap targets, and the status legend must not depend on hover.
