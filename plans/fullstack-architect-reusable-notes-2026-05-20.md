# Fullstack Architect Reusable Notes - 2026-05-20

These notes capture reusable cross-stack lessons from the Application History employee filter work. They are for future planning and coordination; they do not change feature scope.

## Planning Gate

- For unclear product requests, create a frontend/backend plan first and wait for explicit user confirmation before implementation.
- Keep the plan under the project `plans/` directory and make the frontend/backend API contract concrete enough that each side can work independently.
- If the user asks to discuss UI details first, do not code. Capture the options, trade-offs, and recommended decision, then wait for confirmation.

## Frontend Scope Control

- Small UI requests must stay small. Do not use a filter, button, or text truncation change as a reason to redesign the whole page.
- For Application History cards, preserve the compact summary pattern: list cards show scan-friendly information, while full details stay in the record detail modal.
- Avoid duplicate detail entrances. If the card has a single details icon, do not add another `View record`, `Show more`, or expanded-detail control unless the user explicitly approves it.
- Mobile behavior is part of the plan: controls need tappable hit areas, responsive widths, text truncation that does not break layout, and no hover-only interaction.

## Backend/API Boundaries

- Do not overload an existing endpoint when the existing semantics differ. In this case, employee My Applications can include pending records, while admin History must mean non-pending history records.
- Keep authorization in a service/policy layer rather than scattering role checks through controllers or repository methods.
- Name frontend types and backend contracts around the domain capability, such as `application history` and `employee visibility`, rather than hard-coding role names into every component or DTO.
- Preserve extension points for future roles. The current implementation may be Manager-only, but the API should be able to support narrower visibility scopes later without a second rewrite.

## Database Rule

- Agents must not directly apply schema or data changes for this project.
- If a feature needs a table, column, constraint, index, or migration, the plan/comment must include complete SQL and state that the user must execute it.
- If no database change is needed, say that explicitly in the plan so Backend_Dev and Project_Manager can verify the boundary.

## Verification Notes

- Frontend changes should include focused Jest coverage when behavior changes, plus `git diff --check`.
- `npx tsc --noEmit` may currently be blocked by unrelated existing TypeScript errors. When that happens, report the exact unrelated files and confirm the touched History files are not in the error list.
- Backend changes should include focused service/controller/repository tests when API behavior changes, plus the Maven test command that was run.

