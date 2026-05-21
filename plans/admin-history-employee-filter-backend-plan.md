# Admin Application History Employee Filter - Backend Plan

## Scope

Only plan the feature. Do not implement code until the user confirms "ok".

Goal: Support the manager/admin Application -> History employee dropdown filter with a stable REST contract, clear DTOs, service/repository boundaries, and current Manager-only access rules.

Current access boundary: this version is still Manager-only. Team Leader must not be included in this implementation. The backend design must still avoid scattering hard-coded Manager-only checks through controllers or repository queries, so future Team Leader support can be added by changing one permission/scope policy rather than rewriting the History API.

## Repository Context Read

- Frontend README read: `/Users/marktwain/Projects/OPBOA/README.md`.
- Backend README read: `/Users/marktwain/Projects/OPBAdministrationBackend/Readme.md`.
- Backend stack: Spring Boot 3.2.3, Maven, Spring Web, Spring Data JPA, Spring Security.
- Relevant backend packages:
  - Leave applications: `ca.openbox.process`
  - User presentation / employee list: `ca.openbox.user.presentor`, `ca.openbox.user.presentation`, `ca.openbox.user.repository`
  - Security config: `ca.openbox.user.configuration.SecurityConfiguration`

## 1. Current Admin History Data Source, Paging, Sorting, Filtering

Current endpoint:

- `GET /process/application`
- Controller: `LeaveApplicationController.getApplicationsByApplicant(...)`

Current query behavior:

- `handler` non-empty -> `LeaveApplicationService.getApplicationsByHandler(handler)`
  - Repository method: `getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc`
  - Used by review flows.
- `applicant` non-empty -> `LeaveApplicationService.getApplicationsByApplicant(applicant)`
  - Repository method: `getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc`
  - Used by employee My Applications.
  - Returns all statuses, including `pending`.
- no query params -> `LeaveApplicationService.getAllApplications()`
  - Repository method: `getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc("pending")`
  - This is the current manager History data source.

Current limitations:

- No pagination.
- No client-controlled sort.
- Sort is fixed as `submitTime DESC`.
- No History-specific employee filter.
- Existing `applicant` param cannot safely be reused for History because its current meaning includes pending applications.

## 2. Employee Dropdown Data Source

Existing endpoint:

- `GET /presentor/user/employees/basic`
- Controller: `UserPresentor.getEmployeeBasicInfo()`
- Repository: `UserPresentationRepository.findByRolesContainingOrderByActiveDesc("tester")`
- Response entity: `UserPresentation` from table `opb_user`

Current response fields:

```json
{
  "username": "jane",
  "email": "jane@example.com",
  "active": 1,
  "name": "Jane Doe",
  "roles": "tester",
  "groupName": "surrey"
}
```

Issue with current implementation:

- Role `"tester"` is hard-coded.
- It sorts active users first but does not filter inactive users out.
- It returns email, which is not necessary for a dropdown.

Recommended endpoint for this feature:

- `GET /presentor/user/employees/options`

Query params:

- `activeOnly`: optional boolean, default `true`.
- `q`: optional string, reserved for backend search. Can be ignored initially if Frontend_Dev filters client-side.

Response DTO:

```java
public class EmployeeOptionDTO {
    private String username;
    private String name;
    private String roles;
    private String groupName;
    private Integer active;
}
```

Response JSON:

```json
[
  {
    "username": "jane",
    "name": "Jane Doe",
    "roles": "tester",
    "groupName": "surrey",
    "active": 1
  }
]
```

Repository plan:

- Add a method that returns active employees in stable display order.
- Preferred:
  - `List<UserPresentation> findByRolesContainingAndActiveOrderByNameAsc(String role, Integer active);`
- If `active` is mapped inconsistently, use a custom `@Query` with `active = 1`.

Compatibility:

- Keep `GET /presentor/user/employees/basic` unchanged unless Backend_Dev chooses to internally delegate to the new service. Existing Team page uses `getEmployeeBasic()`.

## 3. History Filter API Design

Preferred endpoint:

- `GET /process/application/history`

Rationale:

- Keeps current `GET /process/application` behavior unchanged for Review and My Applications.
- Gives History a clear "non-pending only" contract.
- Allows pagination without breaking existing consumers that expect `LeaveApplication[]`.

Query params:

- `operatorUsername`: required while using the current project's explicit-operator authorization pattern. If backend switches to authenticated principal validation, remove this public param and derive the operator from `Authentication`.
- `employeeUsername`: optional string. Missing, blank, or whitespace means all employees.
- `page`: optional integer, default `0`.
- `size`: optional integer, default `20` or `50`; cap at a reasonable max such as `100`.
- `sort`: optional string, default `submitTime,desc`.

Permission/scope contract:

- Current version: only Manager operators can view History.
- Keep the API shape stable for future roles. Team Leader support, if approved later, should not require a new History filtering endpoint.
- Backend should resolve a visibility policy for the operator:
  - Manager -> `ALL_EMPLOYEES`.
  - Future Team Leader -> `TEAM_MEMBERS`, likely constrained by team/group membership.
- This issue implements only the Manager branch. The Team Leader branch should be documented as a future extension and can throw/deny until explicitly implemented.
- Repository methods should accept the normalized filter and an optional visibility scope from the service. They should not contain role checks.

Request examples:

- All history:
  - `GET /api/process/application/history?operatorUsername=manager1&page=0&size=20&sort=submitTime,desc`
- One employee:
  - `GET /api/process/application/history?operatorUsername=manager1&employeeUsername=jane&page=0&size=20&sort=submitTime,desc`
- Clear filter:
  - frontend keeps `operatorUsername` and omits `employeeUsername`, or sends `employeeUsername=`; backend treats both as all employees.

Response DTO:

```java
public class PageResponseDTO<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String sort;
}
```

Use existing `LeaveApplication` entity/DTO shape for each row unless Backend_Dev wants to formalize `LeaveApplicationDTO`. Current frontend expects:

```json
{
  "id": 123,
  "applicant": "jane",
  "leaveType": "SICK",
  "submitTime": "2026-05-14T09:30:00-07:00",
  "start": "2026-05-20T09:00:00-07:00",
  "end": "2026-05-20T17:00:00-07:00",
  "currentHandler": "jane",
  "status": "approved",
  "reason": "Medical appointment",
  "rejectReason": null,
  "note": ""
}
```

No results response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "sort": "submitTime,desc"
}
```

Sorting:

- Accept only a whitelist:
  - `submitTime,desc`
  - `submitTime,asc`
  - optional later: `start,desc`, `start,asc`
- Reject invalid sort with `400 Bad Request`, or fall back to default. Recommendation: fall back to default for lower UI risk.

## 4. Backend Change Points

Controller:

- Add to `LeaveApplicationController`:
  - `@GetMapping("/application/history")`
  - parameters: `employeeUsername`, `page`, `size`, `sort`, and a manager identity signal if current auth cannot be relied on.
- Controller responsibility should stay thin:
  - parse query params;
  - pass them to service;
  - return `PageResponseDTO<LeaveApplication>`.
- Do not put role branching such as `if manager ... else if team_leader ...` in the controller.

Service:

- Add `LeaveApplicationService.getHistory(String employeeUsername, int page, int size, String sort, String operatorUsername)`.
- Responsibilities:
  - Validate/cap pagination.
  - Normalize blank `employeeUsername` to `null`.
  - Resolve History visibility through a dedicated permission/scope helper.
  - Query repository.
  - Map DOs to `LeaveApplication`.
  - Build `PageResponseDTO<LeaveApplication>`.

Permission/scope helper:

- Add a small dedicated component instead of spreading Manager checks across History code. Suggested names:
  - `ApplicationHistoryAccessPolicy`
  - `HistoryVisibilityPolicy`
  - `ApplicationHistoryPermissionService`
- Suggested return model:

```java
public enum HistoryVisibilityScope {
    ALL_EMPLOYEES,
    TEAM_MEMBERS
}

public class HistoryVisibility {
    private boolean allowed;
    private HistoryVisibilityScope scope;
    private String groupName;
}
```

- Current implementation:
  - Load active operator by `operatorUsername`.
  - If operator has exact role token `Manager` or accepted manager group rule, return allowed with `ALL_EMPLOYEES`.
  - Otherwise throw `403 Forbidden`.
- Future Team Leader extension:
  - If operator has exact role token `team_leader`, return allowed with `TEAM_MEMBERS` and the operator's group/team identifier.
  - Service would then apply that scope when building the repository query so Team Leader can only see their own team members.
  - This issue should not implement Team Leader filtering; it only reserves the boundary.

Repository:

Current repository extends `org.springframework.data.repository.Repository`, not `JpaRepository`.

Recommended minimal additions:

```java
Page<LeaveApplicationDO> findByStatusIsNotContaining(String status, Pageable pageable);

Page<LeaveApplicationDO> findByStatusIsNotContainingAndApplicant(String status, String applicant, Pageable pageable);
```

Future Team Leader scope additions, not part of this implementation:

```java
Page<LeaveApplicationDO> findByStatusIsNotContainingAndApplicantIn(String status, Collection<String> applicants, Pageable pageable);

Page<LeaveApplicationDO> findByStatusIsNotContainingAndApplicantInAndApplicant(String status, Collection<String> applicants, String applicant, Pageable pageable);
```

Alternatively, use a `Specification`/Criteria query later if the visibility rules become more complex. The key design rule is that repositories should receive applicant constraints from the service; they should not inspect operator roles.

If `Page` methods are awkward with the current `Repository` base, switch `LeaveApplicationRepository` to extend `JpaRepository<LeaveApplicationDO, Integer>` and keep existing derived methods. This is a small repository-level refactor but should be tested because delete/save/get methods are used elsewhere.

DTOs:

- Add `PageResponseDTO<T>` under `ca.openbox.process.dto` or a shared DTO package if one exists.
- Add `EmployeeOptionDTO` under `ca.openbox.user.dto` if adding `/employees/options`.
- Existing `LeaveApplicationDTO` appears unused; avoid expanding it unless Backend_Dev wants to standardize leave application responses across endpoints.

Authorization / Admin Permission:

Current security config permits broad GET access:

- `requestMatchers(HttpMethod.GET, "/**").permitAll()`
- process endpoints are also broadly permitted for mutations.

For this feature, Backend_Dev should still avoid ordinary employee over-query by adding a service-level permission policy:

- Preferred if authentication is available:
  - Use Spring `Authentication` / session principal and let `ApplicationHistoryAccessPolicy` verify the role/scope.
- Existing project pattern if auth is not consistently wired:
  - Accept `operatorUsername` query param for this new endpoint and let `ApplicationHistoryAccessPolicy` use `UserRepository`/`UserService` to verify the operator has History access.
  - This is weaker than server-authenticated identity but consistent with existing shift status manager checks that use `operatorUsername`.

Recommended contract if using current project pattern:

- `GET /process/application/history?operatorUsername=<managerUsername>&employeeUsername=<employeeUsername>&page=0&size=20`
- Backend returns `403 Forbidden` if `operatorUsername` is missing, inactive, or not a Manager in this release.
- Frontend passes current `localStorage.user.username`.

Important:

- Do not allow normal employees to call this endpoint and inspect other employees' applications.
- Keep employee My Applications on `GET /process/application?applicant=<self>` for now.
- A proper JWT/session enforcement cleanup should be a separate issue because the current security configuration permits almost everything.

## 5. Authorization Design and Future Team Leader Boundary

Current release:

- Interface authentication/authorization still requires Manager permission.
- `operatorUsername` remains required under the current project pattern unless Backend_Dev switches this endpoint to server-authenticated principal lookup.
- Missing, inactive, or non-Manager operator returns `403 Forbidden`.
- Team Leader is not allowed in this implementation.

Role matching:

- Match role tokens exactly after splitting by `|`, trimming, and comparing case-insensitively.
- Do not use broad substring matching such as `roles.contains("manager")` for authorization.
- If existing code treats `groupName=manager` as Manager, keep that compatibility inside `ApplicationHistoryAccessPolicy`, not in the controller or repository.

Future Team Leader extension:

- Team Leader may be allowed to view only their team members, while Manager can view all employees.
- If `opb_user.groupName` is the accepted team boundary, the policy can resolve `TEAM_MEMBERS` with that `groupName`; the service can fetch usernames in that group and apply `applicant IN (...)` to History queries.
- If a Team Leader sends `employeeUsername` outside their permitted team, service should deny with `403` or return an empty page based on future product decision.
- The History API path and query contract should remain `GET /process/application/history` with optional `employeeUsername`; only the permission/scope policy and employee option visibility need to change.

## 6. Frontend Contract Summary for Backend_Dev

Frontend will call:

- Employee options:
  - `GET /api/presentor/user/employees/options?activeOnly=true`
  - fallback if not added: `GET /api/presentor/user/employees/basic`
- History:
  - `GET /api/process/application/history`
  - params:
    - `operatorUsername`
    - `employeeUsername` optional
    - `page`
    - `size`
    - `sort`

Frontend interprets:

- Missing/empty `employeeUsername` as all employees.
- `content: []` as valid empty state, not an error.
- `403` as "Only managers can view application history."
- Network or 5xx errors as retryable.
- Current Team Leader users should receive `403` if they call the endpoint directly.
- Future Team Leader support should reuse the same response wrapper and filter parameter.

## 7. Test Suggestions

Backend unit tests:

- `getHistory(null, page, size, sort, manager)` returns non-pending applications for all applicants sorted by submit time desc.
- `getHistory("jane", ...)` returns only non-pending applications for `jane`.
- Pending applications are excluded from History even when employee is selected.
- Blank `employeeUsername` behaves like all employees.
- Invalid/oversized page size is normalized or rejected according to implementation decision.
- Non-manager `operatorUsername` returns/throws `403`.
- Missing/inactive operator returns/throws `403`.
- Team Leader `operatorUsername` returns/throws `403` in the current release.
- Authorization checks are covered through `ApplicationHistoryAccessPolicy` or equivalent, not duplicated in controller tests only.

Backend repository tests:

- Page query by status not containing pending.
- Page query by status not containing pending plus applicant.
- Sort by `submitTime DESC`.

API integration tests:

- `GET /process/application/history?operatorUsername=manager1` returns all employees' history.
- `GET /process/application/history?operatorUsername=manager1&employeeUsername=jane`.
- `GET /process/application/history?operatorUsername=manager1&employeeUsername=unknown` returns `200` with empty page.
- Non-manager request returns `403`.
- Team Leader request returns `403` for this release.
- Employee options returns active employees only and stable order.

Frontend/backend联调:

- Manager opens History and sees all non-pending records.
- Manager selects employee and only that employee's approved/rejected history appears.
- Clear filter restores all history.
- Employee with no records shows empty state.
- Normal employee cannot access History endpoint directly.
- Team Leader cannot access History endpoint directly in this release.

## 8. Database / Migration

No database table, field, constraint, or data migration is required for this feature.

Reason:

- `opb_leave_application.applicant` already stores the username needed for employee filtering.
- `opb_user.username`, `name`, `roles`, `groupName`, and `active` already support the employee dropdown.

No SQL is required. Development should not operate on the database for this issue.

Future Team Leader note: if the existing `opb_user.groupName` is accepted as the team boundary, future Team Leader visibility can likely be implemented without a schema change by deriving team member usernames from existing user records. If the business later needs a many-to-many team membership model, cross-group leaders, or historical team membership snapshots, that should be handled as a separate database-design issue with complete SQL supplied for user execution.

## Risks / Questions

- Manager permission is the main risk. Current `SecurityConfiguration` permits all GET requests, so a robust fix requires broader auth work. For this feature, add targeted service-level validation through `ApplicationHistoryAccessPolicy` or equivalent.
- Existing roles are plain strings. Match roles by splitting on `|` and checking exact role token `Manager`, not substring matching, to avoid accidental permissions.
- Team Leader is explicitly out of scope for this implementation. The design reserves a permission/scope boundary so adding Team Leader later changes `ApplicationHistoryAccessPolicy` and employee visibility rules, not the History API contract.
- Confirm inactive employee behavior. Recommendation: active employees only in dropdown; historical records for inactive users remain available in "All employees". A later enhancement can add "include inactive" search.
- If Backend_Dev changes `LeaveApplicationRepository` to extend `JpaRepository`, test existing save/delete/review/history flows because it touches a shared repository.
