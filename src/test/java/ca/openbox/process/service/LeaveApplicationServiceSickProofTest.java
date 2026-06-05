package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dataobject.LeaveApplicationProofDO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.repository.LeaveApplicationProofRepository;
import ca.openbox.process.repository.LeaveApplicationRepository;
import ca.openbox.process.service.components.ApplicationStatusChangeMessageQueue;
import ca.openbox.process.service.components.LeaveApplicationEmailEvent;
import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceSickProofTest {

    private LeaveApplicationRepository leaveApplicationRepository;
    private LeaveApplicationProofRepository proofRepository;
    private SickLeaveProofStorageService storageService;
    private ShiftArrangementRepository shiftArrangementRepository;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        proofRepository = mock(LeaveApplicationProofRepository.class);
        storageService = mock(SickLeaveProofStorageService.class);
        shiftArrangementRepository = mock(ShiftArrangementRepository.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
        leaveApplicationService.leaveApplicationProofRepository = proofRepository;
        leaveApplicationService.sickLeaveProofStorageService = storageService;
        leaveApplicationService.shiftArrangementRepository = shiftArrangementRepository;
        leaveApplicationService.clock = Clock.fixed(Instant.parse("2026-06-04T18:00:00Z"), ZoneId.of("America/Vancouver"));
    }

    @Test
    void addSickLeaveApplicationCreatesRequiredProofRowAndQueuesEmailEvent() throws Exception {
        LeaveApplication application = new LeaveApplication();
        application.setApplicant("alice");
        application.setLeaveType("SICK");
        ZonedDateTime start = ZonedDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        application.setStart(start);
        application.setEnd(start.withHour(17));

        LeaveApplicationDO saved = application.toDO();
        saved.setId(123);
        when(leaveApplicationRepository.save(any(LeaveApplicationDO.class))).thenReturn(saved);
        when(proofRepository.save(any(LeaveApplicationProofDO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ShiftArrangementDO shift = new ShiftArrangementDO();
        shift.setId(1);
        shift.setStart(application.getStart());
        when(shiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(any(), any(), any()))
                .thenReturn(List.of(shift));

        LeaveApplication result = leaveApplicationService.addLeaveApplication(application);

        assertTrue(result.isSickProofRequired());
        assertEquals(false, result.isSickProofSubmitted());
        ArgumentCaptor<LeaveApplicationProofDO> proofCaptor = ArgumentCaptor.forClass(LeaveApplicationProofDO.class);
        verify(leaveApplicationRepository).save(any(LeaveApplicationDO.class));
        verify(proofRepository).save(proofCaptor.capture());
        assertEquals(Instant.parse("2026-06-04T18:00:00Z"), proofCaptor.getValue().getCreatedAt().toInstant());
        assertEquals(Instant.parse("2026-06-04T18:00:00Z"), proofCaptor.getValue().getUpdatedAt().toInstant());
        assertEquals("LEAVE_SUBMITTED", ApplicationStatusChangeMessageQueue.take().getType().name());
    }

    @Test
    void uploadSickProofUpdatesProofRowAndReturnsSubmittedApplication() throws Exception {
        LeaveApplicationDO application = sickApplication();
        LeaveApplicationProofDO proofRow = new LeaveApplicationProofDO();
        proofRow.setApplicationId(77);
        proofRow.setStatus("REQUIRED");
        StoredSickLeaveProof storedProof = new StoredSickLeaveProof(
                "77/20260601120000000_test.pdf",
                "/configured/sick-proof-dir/77/20260601120000000_test.pdf",
                "doctor-note.pdf",
                "application/pdf",
                12L
        );
        when(leaveApplicationRepository.getLeaveApplicationDOById(77)).thenReturn(application);
        when(proofRepository.findById(77)).thenReturn(Optional.of(proofRow));
        when(storageService.store(any(Integer.class), any(MockMultipartFile.class))).thenReturn(storedProof);
        when(proofRepository.save(any(LeaveApplicationProofDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveApplication result = leaveApplicationService.uploadSickProof(
                77,
                "alice",
                new MockMultipartFile("proof", "doctor-note.pdf", "application/pdf", "pdf".getBytes())
        );

        assertTrue(result.isSickProofRequired());
        assertTrue(result.isSickProofSubmitted());
        assertNotNull(result.getSickProofUploadedAt());
        assertEquals("doctor-note.pdf", result.getSickProofOriginalFilename());
        ArgumentCaptor<LeaveApplicationProofDO> proofCaptor = ArgumentCaptor.forClass(LeaveApplicationProofDO.class);
        verify(proofRepository).save(proofCaptor.capture());
        assertEquals(Instant.parse("2026-06-04T18:00:00Z"), proofCaptor.getValue().getCreatedAt().toInstant());
        assertEquals(Instant.parse("2026-06-04T18:00:00Z"), proofCaptor.getValue().getUploadedAt().toInstant());
        assertEquals(Instant.parse("2026-06-04T18:00:00Z"), proofCaptor.getValue().getUpdatedAt().toInstant());
        LeaveApplicationEmailEvent emailEvent = ApplicationStatusChangeMessageQueue.take();
        assertEquals("SICK_PROOF_UPLOADED", emailEvent.getType().name());
        assertEquals("/configured/sick-proof-dir/77/20260601120000000_test.pdf", emailEvent.getSickProofStoredPath());
    }

    @Test
    void nonSickLeaveApplicationDoesNotCreateProofRow() throws Exception {
        LeaveApplication application = new LeaveApplication();
        application.setApplicant("alice");
        application.setLeaveType("personalleave");
        ZonedDateTime start = ZonedDateTime.parse("2026-06-05T09:00:00-07:00[America/Vancouver]");
        application.setStart(start);
        application.setEnd(start.withHour(17));

        LeaveApplicationDO saved = application.toDO();
        saved.setId(124);
        when(leaveApplicationRepository.save(any(LeaveApplicationDO.class))).thenReturn(saved);

        LeaveApplication result = leaveApplicationService.addLeaveApplication(application);

        assertEquals("personalleave", result.getLeaveType());
        verify(proofRepository, never()).save(any(LeaveApplicationProofDO.class));
        assertEquals("LEAVE_SUBMITTED", ApplicationStatusChangeMessageQueue.take().getType().name());
    }

    @Test
    void uploadSickProofAllowsApplicantWhenDbApplicantHasTrailingWhitespaceAndRequestIsTrimmed() throws Exception {
        LeaveApplication result = uploadProofForApplicants("Harsimranjit Kaur ", "Harsimranjit Kaur");

        assertTrue(result.isSickProofSubmitted());
        assertEquals("Harsimranjit Kaur ", result.getApplicant());
        assertEquals("SICK_PROOF_UPLOADED", ApplicationStatusChangeMessageQueue.take().getType().name());
    }

    @Test
    void uploadSickProofAllowsApplicantWhenBothApplicantsHaveTrailingWhitespace() throws Exception {
        LeaveApplication result = uploadProofForApplicants("Harsimranjit Kaur ", "Harsimranjit Kaur ");

        assertTrue(result.isSickProofSubmitted());
        assertEquals("Harsimranjit Kaur ", result.getApplicant());
        assertEquals("SICK_PROOF_UPLOADED", ApplicationStatusChangeMessageQueue.take().getType().name());
    }

    @Test
    void uploadSickProofRejectsApplicantMismatch() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(77)).thenReturn(sickApplication());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.uploadSickProof(
                        77,
                        "mallory",
                        new MockMultipartFile("proof", "doctor-note.pdf", "application/pdf", "pdf".getBytes())
                ));

        assertEquals("403 FORBIDDEN \"Applicant does not match leave application\"", exception.getMessage());
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void pendingApplicationsByHandlerIncludeProofMetadata() {
        LeaveApplicationDO missingProofApplication = sickApplication();
        missingProofApplication.setId(77);
        LeaveApplicationDO submittedProofApplication = sickApplication();
        submittedProofApplication.setId(78);
        LeaveApplicationProofDO submittedProof = submittedProof(78);

        when(leaveApplicationRepository.getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc("raynold"))
                .thenReturn(List.of(missingProofApplication, submittedProofApplication));
        when(proofRepository.findByApplicationIdIn(List.of(77, 78)))
                .thenReturn(List.of(submittedProof));

        List<LeaveApplication> applications = leaveApplicationService.getApplicationsByHandler("raynold");

        assertEquals(2, applications.size());
        assertTrue(applications.get(0).isSickProofRequired());
        assertEquals(false, applications.get(0).isSickProofSubmitted());
        assertEquals(null, applications.get(0).getSickProofUploadedAt());
        assertEquals(null, applications.get(0).getSickProofOriginalFilename());
        assertTrue(applications.get(1).isSickProofRequired());
        assertTrue(applications.get(1).isSickProofSubmitted());
        assertEquals(submittedProof.getUploadedAt(), applications.get(1).getSickProofUploadedAt());
        assertEquals("doctor-note.pdf", applications.get(1).getSickProofOriginalFilename());
    }

    private LeaveApplication uploadProofForApplicants(String dbApplicant, String requestApplicant) {
        LeaveApplicationDO application = sickApplication();
        application.setApplicant(dbApplicant);
        LeaveApplicationProofDO proofRow = new LeaveApplicationProofDO();
        proofRow.setApplicationId(77);
        proofRow.setStatus("REQUIRED");
        StoredSickLeaveProof storedProof = new StoredSickLeaveProof(
                "77/20260601120000000_test.pdf",
                "/configured/sick-proof-dir/77/20260601120000000_test.pdf",
                "doctor-note.pdf",
                "application/pdf",
                12L
        );
        when(leaveApplicationRepository.getLeaveApplicationDOById(77)).thenReturn(application);
        when(proofRepository.findById(77)).thenReturn(Optional.of(proofRow));
        when(storageService.store(any(Integer.class), any(MockMultipartFile.class))).thenReturn(storedProof);
        when(proofRepository.save(any(LeaveApplicationProofDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return leaveApplicationService.uploadSickProof(
                77,
                requestApplicant,
                new MockMultipartFile("proof", "doctor-note.pdf", "application/pdf", "pdf".getBytes())
        );
    }

    private LeaveApplicationDO sickApplication() {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setId(77);
        application.setApplicant("alice");
        application.setLeaveType("SICK");
        application.setStatus("pending");
        application.setStart(ZonedDateTime.now().plusDays(1));
        application.setEnd(ZonedDateTime.now().plusDays(1).plusHours(8));
        application.setSubmitTime(ZonedDateTime.now());
        application.setCurrentHandler("raynold,agnes");
        return application;
    }

    private LeaveApplicationProofDO submittedProof(Integer applicationId) {
        LeaveApplicationProofDO proof = new LeaveApplicationProofDO();
        proof.setApplicationId(applicationId);
        proof.setStatus("SUBMITTED");
        proof.setUploadedAt(ZonedDateTime.parse("2026-06-03T12:00:00-07:00[America/Vancouver]"));
        proof.setOriginalFilename("doctor-note.pdf");
        return proof;
    }
}
