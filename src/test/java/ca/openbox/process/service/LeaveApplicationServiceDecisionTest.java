package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.repository.LeaveApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceDecisionTest {

    private LeaveApplicationRepository leaveApplicationRepository;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
    }

    @Test
    void permitApplicationStoresOptionalReviewCommentAndPreservesApplicantAsCurrentHandler() {
        LeaveApplicationDO application = application(14, "Harsimranjit Kaur ");
        when(leaveApplicationRepository.getLeaveApplicationDOById(14)).thenReturn(application);

        leaveApplicationService.permitApplication(14, "  Approved with handoff required  ");

        assertEquals("approved", application.getStatus());
        assertEquals("Approved with handoff required", application.getReviewComment());
        assertEquals("Harsimranjit Kaur ", application.getCurrentHandler());
        verify(leaveApplicationRepository).save(application);
    }

    @Test
    void permitApplicationStoresBlankReviewCommentAsNull() {
        LeaveApplicationDO application = application(15, "jane");
        when(leaveApplicationRepository.getLeaveApplicationDOById(15)).thenReturn(application);

        leaveApplicationService.permitApplication(15, "   ");

        assertEquals("approved", application.getStatus());
        assertNull(application.getReviewComment());
        verify(leaveApplicationRepository).save(application);
    }

    @Test
    void rejectApplicationRequiresNonblankReviewComment() {
        LeaveApplicationDO application = application(16, "jane");
        when(leaveApplicationRepository.getLeaveApplicationDOById(16)).thenReturn(application);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.rejectApplication(16, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(leaveApplicationRepository, never()).save(application);
    }

    @Test
    void rejectApplicationStoresRequiredReviewCommentAndPreservesApplicantAsCurrentHandler() {
        LeaveApplicationDO application = application(17, "Harsimranjit Kaur ");
        when(leaveApplicationRepository.getLeaveApplicationDOById(17)).thenReturn(application);

        leaveApplicationService.rejectApplication(17, "  Insufficient coverage  ");

        assertEquals("rejected", application.getStatus());
        assertEquals("Insufficient coverage", application.getReviewComment());
        assertEquals("Harsimranjit Kaur ", application.getCurrentHandler());
        verify(leaveApplicationRepository).save(application);
    }

    @Test
    void permitApplicationReturnsNotFoundWhenApplicationIsMissing() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(404)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.permitApplication(404, null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void rejectApplicationReturnsNotFoundWhenApplicationIsMissing() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(404)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.rejectApplication(404, "reason"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private LeaveApplicationDO application(Integer id, String applicant) {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setId(id);
        application.setApplicant(applicant);
        application.setStatus("pending");
        return application;
    }
}
