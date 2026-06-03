# Sick Proof Upload Config Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the backend reads the sick-proof upload directory from `uploads.sick-proof-dir` and can write a small `.txt` file into that configured directory, then clean it up.

**Architecture:** Add only test-scoped backend code that reads `uploads.sick-proof-dir` from the same external configuration source used by the running application and writes one small `.txt` file to that directory. This is backend-only validation; no production Spring bean, REST endpoint, DTO contract, JPA entity, repository, database migration, or frontend UI change is required for this issue.

**Tech Stack:** Java 17, Spring Boot 3.2.3, Maven, JUnit 5, `java.nio.file.Files`, React Native Web frontend unchanged.

---

## Context From Current Projects

- Backend project: `/Users/marktwain/Projects/OPBAdministrationBackend`
- Frontend project: `/Users/marktwain/Projects/OPBOA`
- Backend README says the app uses Spring Boot 3.2.3, Maven, `/api` servlet context path, and package-based controller/service/repository structure.
- Backend config currently contains the required key:

```yaml
uploads:
  sick-proof-dir: {{sickProofDir}}
```

- The exact configured path must not be hardcoded in code or in the test. The implementation must use Spring property binding with the exact key `uploads.sick-proof-dir`.
- Important config-source rule: the test must not silently fall back to `src/main/resources/application.yml`, because that file is only a template and still contains `{{sickProofDir}}`. `Main.main()` sets `spring.config.location` to `file:/etc/openbox/config.yml`; a narrow test context that does not invoke `Main.main()` is not enough by itself to prove the runtime file config is active.
- Existing frontend leave application requests use JSON through `request/LeaveApplicationRequest.ts`; there is no current multipart sick-proof upload request in the frontend.
- Existing backend leave application flow is in `ca.openbox.process`, especially `LeaveApplicationController` and `LeaveApplicationService`. This plan does not change that flow because the request is only to validate backend config reading and file write capability.

## Scope Decisions

- Backend: required.
- Frontend: no change for this issue. There is no new user-facing sick-proof upload API in the request, so adding a React component, mobile layout, TypeScript request type, or file picker would create product scope that has not been approved.
- REST API endpoints: no new endpoint in this plan. The test writes through a test-scoped helper directly. If a future user story asks employees to upload proof from the UI, add an endpoint such as `POST /api/process/application/{applicationID}/sick-proof` with multipart handling in a separate plan.
- Service layer: no production service for this issue. Use a test-scoped helper inside `src/test/java` so the probe does not affect application startup or runtime component scanning.
- Repository layer: none. File storage is filesystem-backed and does not need JPA.
- DTOs / entities: none for this issue. No API payload or database row is being introduced.
- Database / SQL: no schema, data, constraint, or migration change.

## File Structure

- Create: `src/test/java/ca/openbox/process/service/SickProofStorageServiceConfigTest.java`
  - Responsibility: load a test-only Spring context using the same external config source as the application, bind the exact property key, assert it resolves to a real non-template path, write one small `.txt` file, assert content exists in that configured directory, and delete only the file it created.
- Optional test-only helper location if the implementation wants a separate class instead of a nested class: `src/test/java/ca/openbox/process/service/SickProofTestFileWriter.java`
  - Responsibility: keep file-write helper logic out of production code while making the test readable.
- No frontend files modified.
- No repository, entity, or DTO files modified.
- No `src/main/java` files modified.

## Backend Contract

### Configuration Key

Use the exact key:

```java
@Value("${uploads.sick-proof-dir}")
private String sickProofDir;
```

or constructor injection with the same property:

```java
SickProofTestFileWriter(@Value("${uploads.sick-proof-dir}") String sickProofDir) {
    this.sickProofDirectory = Path.of(sickProofDir).toAbsolutePath().normalize();
}
```

Constructor injection is preferred because the test can instantiate or load the helper predictably.

### Test-Scoped Helper API

```java
package ca.openbox.process.service;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class SickProofTestFileWriter {
    private final Path sickProofDirectory;

    SickProofTestFileWriter(@Value("${uploads.sick-proof-dir}") String sickProofDir) {
        if (sickProofDir == null || sickProofDir.isBlank()) {
            throw new IllegalArgumentException("uploads.sick-proof-dir must be configured");
        }
        if (sickProofDir.contains("{{") || sickProofDir.contains("}}")) {
            throw new IllegalArgumentException("uploads.sick-proof-dir still contains an unresolved template value");
        }
        this.sickProofDirectory = Path.of(sickProofDir).toAbsolutePath().normalize();
    }

    public Path getSickProofDirectory() {
        return sickProofDirectory;
    }

    public Path writeTextProof(String filename, String content) throws IOException {
        Files.createDirectories(sickProofDirectory);
        Path target = sickProofDirectory.resolve(filename).normalize();
        if (!target.startsWith(sickProofDirectory)) {
            throw new IllegalArgumentException("filename must stay inside uploads.sick-proof-dir");
        }
        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }
}
```

### Test API

The test must not hardcode the configured directory. It should receive the path through Spring via a test-only bean or test-only helper bound to `uploads.sick-proof-dir`.

Because the application config location is set inside `Main.main()`, the implementation must choose one of these two config-loading strategies before running the test:

1. Preferred for proving application behavior: load the Spring Boot test through `Main` and force Spring Boot to invoke the main method, for example `@SpringBootTest(classes = Main.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)`. This exercises the default `file:/etc/openbox/config.yml` location set by the application entrypoint. If the full application context fails because unrelated database or scheduled-job beans are unavailable, stop and report that a focused test cannot safely use the full main context without broader test configuration.
2. Acceptable focused fallback: keep the test context narrow, but run it with an explicit config location that matches the application external file, for example `mvn test -Dtest=SickProofStorageServiceConfigTest -Dspring.config.location=file:/etc/openbox/config.yml`. This does not execute `Main.main()`, but it still verifies the same file config source rather than the classpath template.

Do not use the original narrow test command alone, because it can read only classpath/default test configuration and fail against the unresolved template instead of validating the user-edited external file.

```java
package ca.openbox.process.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.openbox.Main;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {Main.class, SickProofStorageServiceConfigTest.TestConfig.class},
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS
)
class SickProofStorageServiceConfigTest {

    @Autowired
    private SickProofTestFileWriter sickProofTestFileWriter;

    @Test
    void writesSmallTxtFileToConfiguredSickProofDirectory() throws Exception {
        Path configuredDirectory = sickProofTestFileWriter.getSickProofDirectory();
        assertFalse(configuredDirectory.toString().isBlank());

        String filename = "sick-proof-config-test-" + UUID.randomUUID() + ".txt";
        Path writtenFile = null;
        try {
            writtenFile = sickProofTestFileWriter.writeTextProof(filename, "sick proof config test");

            assertTrue(writtenFile.startsWith(configuredDirectory));
            assertTrue(Files.exists(writtenFile));
            assertEquals(
                    "sick proof config test",
                    Files.readString(writtenFile, StandardCharsets.UTF_8)
            );
        } finally {
            if (writtenFile != null) {
                Files.deleteIfExists(writtenFile);
            }
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        SickProofTestFileWriter sickProofTestFileWriter(
                @Value("${uploads.sick-proof-dir}") String sickProofDir
        ) {
            return new SickProofTestFileWriter(sickProofDir);
        }
    }
}

class SickProofTestFileWriter {
    private final Path sickProofDirectory;

    SickProofTestFileWriter(String sickProofDir) {
        if (sickProofDir == null || sickProofDir.isBlank()) {
            throw new IllegalArgumentException("uploads.sick-proof-dir must be configured");
        }
        if (sickProofDir.contains("{{") || sickProofDir.contains("}}")) {
            throw new IllegalArgumentException("uploads.sick-proof-dir still contains an unresolved template value");
        }
        this.sickProofDirectory = Path.of(sickProofDir).toAbsolutePath().normalize();
    }

    Path getSickProofDirectory() {
        return sickProofDirectory;
    }

    Path writeTextProof(String filename, String content) throws IOException {
        Files.createDirectories(sickProofDirectory);
        Path target = sickProofDirectory.resolve(filename).normalize();
        if (!target.startsWith(sickProofDirectory)) {
            throw new IllegalArgumentException("filename must stay inside uploads.sick-proof-dir");
        }
        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }
}
```

## Task 1: Add Test-Only Backend File Writer

**Files:**
- Create: `src/test/java/ca/openbox/process/service/SickProofStorageServiceConfigTest.java`
- Optional create: `src/test/java/ca/openbox/process/service/SickProofTestFileWriter.java`

- [ ] **Step 1: Add the test-only helper**

Use the helper shown in the Test-Scoped Helper API section. It may be a package-private nested or sibling test class. Do not annotate it with `@Service`, and do not place it under `src/main/java`.

- [ ] **Step 2: Review the helper responsibility**

Confirm it only handles:

- binding `uploads.sick-proof-dir`
- validating the configured string is not blank or unresolved template text
- resolving a normalized directory path
- writing a `.txt` file safely inside that directory

Do not add leave-application approval logic, application IDs, database writes, or controller code in this task.

## Task 2: Add Spring Config/File Write Test

**Files:**
- Create: `src/test/java/ca/openbox/process/service/SickProofStorageServiceConfigTest.java`

- [ ] **Step 1: Add the Spring test file**

Use the test code shown in the Test API section. The test registers the helper through `@TestConfiguration`, which keeps the helper test-scoped and avoids adding a production bean.

If the full `Main` context is blocked by unrelated infrastructure requirements, use the focused fallback but still keep all helper/config code under `src/test/java`. If using the fallback, document the exact test command with `-Dspring.config.location=file:/etc/openbox/config.yml`.

- [ ] **Step 2: Verify cleanup behavior**

The test creates a unique file named `sick-proof-config-test-<uuid>.txt` and deletes only that file in `finally`. It must not delete the configured directory or any existing user files.

## Task 3: Run Focused Backend Verification

**Files:**
- Verify: `src/test/java/ca/openbox/process/service/SickProofStorageServiceConfigTest.java`
- Optional verify: `src/test/java/ca/openbox/process/service/SickProofTestFileWriter.java`

- [ ] **Step 1: Run the focused test**

```bash
mvn test -Dtest=SickProofStorageServiceConfigTest
```

Expected for the preferred strategy: Spring Boot invokes `Main.main()`, applies its default `spring.config.location=file:/etc/openbox/config.yml`, and the test writes then deletes one small `.txt` file under the directory resolved from `uploads.sick-proof-dir`.

If the implementation uses the focused fallback, run:

```bash
mvn test -Dtest=SickProofStorageServiceConfigTest -Dspring.config.location=file:/etc/openbox/config.yml
```

Expected for the fallback strategy: the test still reads the same external file config source and does not rely on `src/main/resources/application.yml`.

- [ ] **Step 2: If the test reads the classpath template instead of the external file**

Expected failure signal: `uploads.sick-proof-dir still contains an unresolved template value` and the resolved source is effectively the classpath template.

Resolution: fix the test configuration source. Either use `useMainMethod = ALWAYS` with `Main.class` or pass `-Dspring.config.location=file:/etc/openbox/config.yml`. Do not hardcode the configured directory.

- [ ] **Step 3: If the test fails because the key is missing**

Expected failure signal: Spring cannot resolve `${uploads.sick-proof-dir}`.

Resolution: stop and report that the runtime/test configuration does not expose the required key. Do not hardcode the path in code.

- [ ] **Step 4: If the test fails because the value still contains template braces after the external file is confirmed**

Expected failure signal: `uploads.sick-proof-dir still contains an unresolved template value`.

Resolution: stop and report that the config still has a template placeholder instead of a real path. Do not write into a guessed directory.

- [ ] **Step 5: If the test fails because the directory is not writable**

Expected failure signal: `AccessDeniedException`, `NoSuchFileException` after directory creation fails, or another `IOException`.

Resolution: stop and report the filesystem permission/path issue. Do not change file permissions automatically unless the user explicitly approves the exact operation.

## Task 4: Cross-Stack Interaction Review

**Files:**
- Review only: `/Users/marktwain/Projects/OPBOA/request/LeaveApplicationRequest.ts`
- Review only: `/Users/marktwain/Projects/OPBOA/app/applications/NewApplication.tsx`
- Review only: `/Users/marktwain/Projects/OPBAdministrationBackend/src/main/java/ca/openbox/process/controller/LeaveApplicationController.java`

- [ ] **Step 1: Confirm there is no frontend API contract change**

Current frontend leave application submission sends JSON to:

```text
PUT ${EXPO_PUBLIC_API_URL}api/process/application/leave-application
```

This plan does not add multipart upload fields or change this request.

- [ ] **Step 2: Confirm no React component hierarchy change**

No frontend components are modified. The existing hierarchy around leave applications remains:

```text
app/applications/NewApplication.tsx
  -> request/LeaveApplicationRequest.ts
  -> model/LeaveDateAvailability.ts and model/LeaveApplication.ts
```

If a later approved requirement asks for employee proof upload, plan a mobile-friendly UI separately:

```text
NewApplication.tsx
  -> SickProofPicker.tsx
  -> LeaveApplicationRequest.uploadSickProof(...)
  -> multipart POST backend endpoint
```

That future UI is outside this issue.

## Task 5: Final Developer Handoff

- [ ] **Step 1: Summarize implementation result in the issue**

Include:

- focused test command run
- whether `uploads.sick-proof-dir` resolved successfully
- whether the `.txt` file write/read/delete check passed
- explicit statement that frontend was unchanged

- [ ] **Step 2: Do not change issue status unless explicitly requested**

The current instruction says to wait for user confirmation before implementation, so status should remain unchanged during plan handoff.

## Self-Review

- Requirement coverage: the plan verifies exact key `uploads.sick-proof-dir`, loads the external config source rather than the classpath template, writes a small `.txt` file to the configured directory, and cleans up that file.
- Backend layering: because this issue is only a probe test, all executable code is test-scoped. No production service, controller, repository, DTO, JPA entity, or DB table is needed.
- Frontend interaction: no frontend change is required because no upload UI/API contract has been requested. The plan documents the current JSON leave application request and the future component/API shape only as out-of-scope guidance.
- Database safety: no SQL is required.
- Placeholder check: the implementation plan intentionally fails fast if the config still contains unresolved `{{...}}` text. The plan now distinguishes between "test accidentally loaded the classpath template" and "the confirmed external config file still has an unresolved value."
- Runtime safety: no `src/main/java` bean is added, so the probe cannot introduce a new startup-time dependency into normal application runs. The only filesystem side effect is the unique test file, which is deleted in `finally`.
