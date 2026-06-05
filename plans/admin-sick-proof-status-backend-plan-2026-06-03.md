# Admin Sick Proof Status Backend Plan - 2026-06-03

## Goal

Support the frontend admin proof-status UI for sick leave applications by keeping proof metadata available on pending review and history responses.

This is a backend implementation plan only. Do not write code until the user approves this plan.

## Product Decision From Discussion

- HR needs to know whether sick leave proof is missing or submitted.
- First version should expose proof status on existing leave application workflows.
- Do not add missing-proof filtering in this version.
- Do not introduce proof approval, proof rejection, file versioning, batch proof operations, or a separate proof management module.

## Existing Backend Structure

Package: `ca.openbox.process`

Relevant files:

- Controller: `src/main/java/ca/openbox/process/controller/LeaveApplicationController.java`
- Service: `src/main/java/ca/openbox/process/service/LeaveApplicationService.java`
- Application entity/response model: `src/main/java/ca/openbox/process/entities/LeaveApplication.java`
- Application persistence model: `src/main/java/ca/openbox/process/dataobject/LeaveApplicationDO.java`
- Proof persistence model: `src/main/java/ca/openbox/process/dataobject/LeaveApplicationProofDO.java`
- Application repository: `src/main/java/ca/openbox/process/repository/LeaveApplicationRepository.java`
- Proof repository: `src/main/java/ca/openbox/process/repository/LeaveApplicationProofRepository.java`
- History access policy: `src/main/java/ca/openbox/process/service/ApplicationHistoryAccessPolicy.java`

Existing API behavior:

- `GET /api/process/application?handler=<username>` returns pending applications for a handler and already calls `enrichApplications(...)`.
- `GET /api/process/application/history?...` returns paged history and already calls `enrichApplications(...)`.
- `LeaveApplication` already returns:
  - `sickProofRequired`
  - `sickProofSubmitted`
  - `sickProofUploadedAt`
  - `sickProofOriginalFilename`

## Recommended Backend Scope

The backend requirement is to ensure admin list endpoints return enriched `LeaveApplication` objects with proof metadata. No new filter parameter is planned for this version.

The implementation should keep existing request shapes unchanged:

- `GET /api/process/application?handler=<username>`
- `GET /api/process/application/history?operatorUsername=<username>&employeeUsername=<optional>&page=0&size=20&sort=submitTime,desc`

## API Contract

Pending review:

```http
GET /api/process/application?handler=<username>
```

History:

```http
GET /api/process/application/history?operatorUsername=<username>&employeeUsername=<optional>&page=0&size=20&sort=submitTime,desc
```

Response shape remains unchanged:

- Pending review returns `List<LeaveApplication>`.
- History returns `PageResponseDTO<LeaveApplication>`.
- Existing fields stay backward compatible.

## Service Design

Keep responsibilities separated:

- Controller keeps existing query params and passes them to service.
- Service owns enrichment and response mapping.
- Repository continues to retrieve application rows.
- `enrichApplications(...)` continues to merge proof rows into response objects.

Pending review path:

1. Controller keeps `GET /application?handler=...` unchanged.
2. Service loads applications by handler as today.
3. Service enriches proof data as today.
4. Return the enriched list.

History path:

1. Controller keeps `GET /application/history` query params unchanged.
2. Service loads the paged application history as today.
3. Service enriches page content with proof data as today.
4. Return the existing `PageResponseDTO<LeaveApplication>` with accurate totals from the existing repository query.

## Repository Plan

No repository changes are required for this version if current endpoints already enrich proof fields.

Keep existing repository methods:

- `getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(...)`
- `getLeaveApplicationDOByStatusIsNotContaining(...)`
- `getLeaveApplicationDOByStatusIsNotContainingAndApplicant(...)`

Do not add proof-status query methods until filtering is explicitly approved later.

## Data Model And DTO Plan

No new DTO is required.

Continue using `LeaveApplication` as the response model. It already provides the frontend contract:

```java
private boolean sickProofRequired;
private boolean sickProofSubmitted;
private ZonedDateTime sickProofUploadedAt;
private String sickProofOriginalFilename;
```

Do not add proof status strings such as `MISSING` or `SUBMITTED` to the response unless the frontend team explicitly asks for backend-precomputed display state. The frontend can derive display labels from the existing boolean fields.

Do not add proof fields to `LeaveApplicationDO`; proof metadata belongs in `LeaveApplicationProofDO`.

## Database And SQL

If `opb_leave_application_proof` already exists in the target environment, this admin display work requires no new table, field, constraint, or data migration.

If the target environment has not yet executed the sick proof table migration, the proof table is a prerequisite. The user or DB owner must execute this SQL; agents must not execute it directly.

```sql
CREATE TABLE opb_leave_application_proof (
  application_id INT NOT NULL,
  proof_type VARCHAR(50) NOT NULL DEFAULT 'SICK_LEAVE_PROOF',
  status VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
  uploaded_at DATETIME(6) NULL,
  original_filename VARCHAR(255) NULL,
  stored_filename VARCHAR(512) NULL,
  content_type VARCHAR(100) NULL,
  file_size_bytes BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (application_id),
  CONSTRAINT fk_leave_application_proof_application
    FOREIGN KEY (application_id)
    REFERENCES opb_leave_application (id)
    ON DELETE CASCADE,
  CONSTRAINT chk_leave_application_proof_type
    CHECK (proof_type = 'SICK_LEAVE_PROOF'),
  CONSTRAINT chk_leave_application_proof_status
    CHECK (status IN ('REQUIRED', 'SUBMITTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_leave_application_proof_type_status
  ON opb_leave_application_proof (proof_type, status);
```

```sql
INSERT INTO opb_leave_application_proof (
  application_id,
  proof_type,
  status,
  created_at,
  updated_at
)
SELECT
  leave_application.id,
  'SICK_LEAVE_PROOF',
  'REQUIRED',
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6)
FROM opb_leave_application leave_application
LEFT JOIN opb_leave_application_proof proof
  ON proof.application_id = leave_application.id
WHERE UPPER(leave_application.leave_type) = 'SICK'
  AND proof.application_id IS NULL;
```

## Backend Task Decomposition

1. Confirm `GET /application?handler=...` and `GET /application/history` return proof fields in local test fixtures.
2. If either endpoint does not include proof fields, fix the enrichment path so it uses `enrichApplications(...)`.
3. Keep controller request params unchanged; do not add `proofStatus`.
4. Keep repository queries unchanged; do not add proof filter queries.
5. Add or adjust tests proving pending review and history responses include proof metadata.
6. Run focused backend verification.

## Tests

Suggested focused tests:

- `LeaveApplicationServiceSickProofTest`
  - pending review returns all handler records with proof metadata enriched.
  - sick records without submitted proof map to `sickProofRequired=true` and `sickProofSubmitted=false`.
  - sick records with submitted proof map to `sickProofSubmitted=true`, upload time, and original filename.

- `LeaveApplicationHistoryProofStatusTest`
  - history returns proof metadata in each `LeaveApplication`.
  - `employeeUsername` history filtering still works.
  - history pagination totals remain unchanged because no proof-status filter is applied.

- `LeaveApplicationControllerProofStatusTest`
  - controller responses include proof fields for pending review.
  - controller responses include proof fields for history.
  - controller does not require or document `proofStatus`.

Focused verification command:

```bash
mvn test -Dtest=LeaveApplicationServiceSickProofTest,LeaveApplicationHistoryProofStatusTest,LeaveApplicationControllerProofStatusTest
```

Broader verification before handoff:

```bash
mvn test
mvn package -DskipTests
```

## Out Of Scope

- Proof file download/view endpoint.
- Proof approval or rejection state.
- Storing proof reviewer, verification result, or audit trail.
- Changing email behavior.
- Changing sick proof upload validation or storage.
- Missing-proof filter query parameters.
- Direct database execution by agents.
