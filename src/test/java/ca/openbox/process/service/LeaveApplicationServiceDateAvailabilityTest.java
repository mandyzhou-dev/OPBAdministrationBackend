package ca.openbox.process.service;

import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.repository.LeaveApplicationRepository;
import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceDateAvailabilityTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

    private LeaveApplicationRepository leaveApplicationRepository;
    private ShiftArrangementRepository shiftArrangementRepository;
    private LeaveApplicationService leaveApplicationService;

    @BeforeEach
    void setUp() {
        leaveApplicationRepository = mock(LeaveApplicationRepository.class);
        shiftArrangementRepository = mock(ShiftArrangementRepository.class);
        leaveApplicationService = new LeaveApplicationService();
        leaveApplicationService.leaveApplicationRepository = leaveApplicationRepository;
        leaveApplicationService.shiftArrangementRepository = shiftArrangementRepository;
        leaveApplicationService.clock = Clock.fixed(Instant.parse("2026-05-27T16:00:00Z"), VANCOUVER);
    }

    @Test
    void availabilityReturnsOneEntryPerDateWithScheduledShiftIds() {
        when(shiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(
                eq("employee1"), any(ZonedDateTime.class), any(ZonedDateTime.class)
        )).thenReturn(List.of(
                shift(123, "employee1", vancouverTime(2026, 5, 27, 9)),
                shift(124, "employee1", vancouverTime(2026, 5, 27, 15))
        ));

        LeaveDateAvailabilityDTO response = leaveApplicationService.getLeaveDateAvailability(
                "employee1",
                LocalDate.parse("2026-05-27"),
                LocalDate.parse("2026-05-28")
        );

        assertEquals("employee1", response.getApplicant());
        assertEquals("2026-05-27", response.getFrom());
        assertEquals("2026-05-28", response.getTo());
        assertEquals("America/Vancouver", response.getBusinessZone());
        assertEquals(2, response.getDates().size());
        assertEquals("2026-05-27", response.getDates().get(0).getDate());
        assertTrue(response.getDates().get(0).isScheduled());
        assertEquals(List.of(123, 124), response.getDates().get(0).getShiftIds());
        assertEquals("2026-05-28", response.getDates().get(1).getDate());
        assertEquals(List.of(), response.getDates().get(1).getShiftIds());
    }

    @Test
    void nonSickLeaveBeforeVancouverTodayIsRejectedBeforeSave() {
        LeaveApplication application = application("personalleave", vancouverTime(2026, 5, 26, 9), vancouverTime(2026, 5, 26, 17));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.addLeaveApplication(application));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Leave date cannot be before today", exception.getReason());
        verify(leaveApplicationRepository, never()).save(any());
    }

    @Test
    void sickLeaveWithoutShiftOnEverySelectedDateIsRejectedBeforeSave() {
        LeaveApplication application = application("SICK", vancouverTime(2026, 5, 28, 9), vancouverTime(2026, 5, 29, 17));
        when(shiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(
                eq("employee1"), any(ZonedDateTime.class), any(ZonedDateTime.class)
        )).thenReturn(List.of(shift(123, "employee1", vancouverTime(2026, 5, 28, 9))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leaveApplicationService.addLeaveApplication(application));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Sick leave requires an existing scheduled shift for every selected date", exception.getReason());
        verify(leaveApplicationRepository, never()).save(any());
    }

    private LeaveApplication application(String leaveType, ZonedDateTime start, ZonedDateTime end) {
        LeaveApplication application = new LeaveApplication();
        application.setApplicant("employee1");
        application.setLeaveType(leaveType);
        application.setStart(start);
        application.setEnd(end);
        application.setStatus("pending");
        return application;
    }

    private ShiftArrangementDO shift(Integer id, String username, ZonedDateTime start) {
        ShiftArrangementDO shift = new ShiftArrangementDO();
        shift.setId(id);
        shift.setUsername(username);
        shift.setStart(start);
        shift.setEnd(start.plusHours(8));
        shift.setStatus("active");
        return shift;
    }

    private ZonedDateTime vancouverTime(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, VANCOUVER)
                .withZoneSameInstant(ZoneId.of("UTC"));
    }
}
