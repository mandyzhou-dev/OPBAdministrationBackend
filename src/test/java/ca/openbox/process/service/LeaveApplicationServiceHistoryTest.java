package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.entities.HistoryVisibility;
import ca.openbox.process.entities.HistoryVisibilityScope;
import ca.openbox.process.entities.LeaveApplication;
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
    private ApplicationHistoryAccessPolicy accessPolicy;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        accessPolicy = mock(ApplicationHistoryAccessPolicy.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
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
    void historyWithEmployeeReturnsOnlyThatApplicant() {
        when(leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingAndApplicant(eq("pending"), eq("jane"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(1, "jane", "approved")), PageRequest.of(0, 20), 1));

        PageResponseDTO<LeaveApplication> response = leaveApplicationService.getHistory(" jane ", 0, 20, "submitTime,desc", "manager");

        assertEquals(1, response.getContent().size());
        assertEquals("jane", response.getContent().get(0).getApplicant());
        verify(leaveApplicationRepository).getLeaveApplicationDOByStatusIsNotContainingAndApplicant(eq("pending"), eq("jane"), any(Pageable.class));
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

    private LeaveApplicationDO application(Integer id, String applicant, String status) {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setId(id);
        application.setApplicant(applicant);
        application.setStatus(status);
        application.setSubmitTime(ZonedDateTime.now());
        return application;
    }
}
