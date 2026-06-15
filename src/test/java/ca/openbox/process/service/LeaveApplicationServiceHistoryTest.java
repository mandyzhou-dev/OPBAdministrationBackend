package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dataobject.LeaveApplicationProofDO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.entities.HistoryVisibility;
import ca.openbox.process.entities.HistoryVisibilityScope;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.repository.LeaveApplicationProofRepository;
import ca.openbox.process.repository.LeaveApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceHistoryTest {

    private LeaveApplicationRepository leaveApplicationRepository;
    private LeaveApplicationProofRepository proofRepository;
    private ApplicationHistoryAccessPolicy accessPolicy;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        proofRepository = mock(LeaveApplicationProofRepository.class);
        accessPolicy = mock(ApplicationHistoryAccessPolicy.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
        leaveApplicationService.leaveApplicationProofRepository = proofRepository;
        leaveApplicationService.applicationHistoryAccessPolicy = accessPolicy;
        when(accessPolicy.resolveVisibility("manager"))
                .thenReturn(new HistoryVisibility(true, HistoryVisibilityScope.ALL_EMPLOYEES, null));
    }

    @Test
    void historyWithoutEmployeeReturnsPagedNonPendingApplications() {
        LeaveApplicationDO approved = application(1, "jane", "approved");
        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(approved), PageRequest.of(0, 20), 1));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory(null, 0, 20, "submitTime,desc", "manager");

        assertEquals(1, response.getContent().size());
        assertEquals("jane", response.getContent().get(0).getApplicant());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertEquals("submitTime,desc", response.getSort());
        verify(leaveApplicationRepository).getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class));
    }

    @Test
    void historyWithEmployeePreservesApplicantQueryValue() {
        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingAndApplicant(eq("pending"), eq("Harsimranjit Kaur "), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(1, "Harsimranjit Kaur ", "approved")), PageRequest.of(0, 20), 1));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory("Harsimranjit Kaur ", 0, 20, "submitTime,desc", "manager");

        assertEquals(1, response.getContent().size());
        assertEquals("Harsimranjit Kaur ", response.getContent().get(0).getApplicant());
        verify(leaveApplicationRepository).getLeaveApplicationDOByStatusIsNotContainingAndApplicant(eq("pending"), eq("Harsimranjit Kaur "), any(Pageable.class));
    }

    @Test
    void blankEmployeeBehavesLikeAllEmployees() {
        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory("   ", 0, 20, "submitTime,desc", "manager");

        assertEquals(List.of(), response.getContent());
        assertEquals(0, response.getTotalElements());
        verify(leaveApplicationRepository).getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class));
    }

    @Test
    void invalidPageSizeAndSortAreNormalized() {
        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory(null, -1, 999, "badField,asc", "manager");

        assertEquals(0, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals("submitTime,desc", response.getSort());
    }

    @Test
    void historyIncludesSickProofMetadataWithoutChangingPaginationTotals() {
        LeaveApplicationDO submittedSickLeave = application(10, "jane", "approved");
        submittedSickLeave.setLeaveType("SICK");
        LeaveApplicationDO missingSickLeave = application(11, "bob", "rejected");
        missingSickLeave.setLeaveType("SICK");
        LeaveApplicationProofDO submittedProof = submittedProof(10);

        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(submittedSickLeave, missingSickLeave), PageRequest.of(0, 20), 42));
        when(proofRepository.findByApplicationIdIn(List.of(10, 11)))
                .thenReturn(List.of(submittedProof));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory(null, 0, 20, "submitTime,desc", "manager");

        assertEquals(2, response.getContent().size());
        assertEquals(42, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        LeaveApplication submitted = response.getContent().get(0);
        assertEquals(10, submitted.getId());
        assertEquals(true, submitted.isSickProofRequired());
        assertEquals(true, submitted.isSickProofSubmitted());
        assertEquals(submittedProof.getUploadedAt(), submitted.getSickProofUploadedAt());
        assertEquals("doctor-note.pdf", submitted.getSickProofOriginalFilename());

        LeaveApplication missing = response.getContent().get(1);
        assertEquals(11, missing.getId());
        assertEquals(true, missing.isSickProofRequired());
        assertEquals(false, missing.isSickProofSubmitted());
        assertEquals(null, missing.getSickProofUploadedAt());
        assertEquals(null, missing.getSickProofOriginalFilename());
    }

    private LeaveApplicationDO application(Integer id, String applicant, String status) {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setId(id);
        application.setApplicant(applicant);
        application.setStatus(status);
        application.setSubmitTime(ZonedDateTime.now());
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
