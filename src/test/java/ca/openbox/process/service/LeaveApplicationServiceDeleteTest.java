package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.repository.LeaveApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceDeleteTest {

    private LeaveApplicationRepository leaveApplicationRepository;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
    }

    @Test
    void canDeleteApplicationStatusNormalizesPendingAndDraft() {
        assertTrue(leaveApplicationService.canDeleteApplicationStatus(" pending "));
        assertTrue(leaveApplicationService.canDeleteApplicationStatus("DRAFT"));
    }

    @Test
    void deleteApplicationDeletesPendingApplication() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(12)).thenReturn(application(12, " pending "));

        leaveApplicationService.deleteApplication(12);

        verify(leaveApplicationRepository).deleteById(12);
    }

    @Test
    void deleteApplicationReturnsNotFoundWhenApplicationIsMissing() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(99)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.deleteApplication(99));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(leaveApplicationRepository, never()).deleteById(99);
    }

    @Test
    void deleteApplicationReturnsConflictForNonDeletableStatus() {
        when(leaveApplicationRepository.getLeaveApplicationDOById(44)).thenReturn(application(44, "approved"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.deleteApplication(44));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(leaveApplicationRepository, never()).deleteById(44);
    }

    @Test
    void deleteApplicationReturnsConflictForUnknownBlankOrNullStatus() {
        assertConflictForStatus("submitted");
        assertConflictForStatus("   ");
        assertConflictForStatus(null);
    }

    private void assertConflictForStatus(String status) {
        LeaveApplicationRepository repository = mock(LeaveApplicationRepository.class);
        LeaveApplicationService service = new LeaveApplicationService();
        service.leaveApplicationRepository = repository;
        when(repository.getLeaveApplicationDOById(55)).thenReturn(application(55, status));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deleteApplication(55));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).deleteById(55);
    }

    private LeaveApplicationDO application(Integer id, String status) {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setId(id);
        application.setStatus(status);
        return application;
    }
}
