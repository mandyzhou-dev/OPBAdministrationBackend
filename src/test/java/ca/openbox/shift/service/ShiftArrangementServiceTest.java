package ca.openbox.shift.service;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dto.PaidSickLeaveQuotaDTO;
import ca.openbox.shift.dto.ShiftCandidateDTO;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.presentation.UserPresentation;
import ca.openbox.user.repository.UserPresentationRepository;
import ca.openbox.user.repository.UserRepository;
import ca.openbox.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShiftArrangementServiceTest {

    private ShiftArrangementRepository shiftArrangementRepository;
    private UserRepository userRepository;
    private UserPresentationRepository userPresentationRepository;
    private EmployeePreferWorkdayBoardService employeePreferWorkdayBoardService;
    private UserService userService;
    private ShiftArrangementService shiftArrangementService;

    @BeforeEach
    void setUp() {
        shiftArrangementRepository = mock(ShiftArrangementRepository.class);
        userRepository = mock(UserRepository.class);
        userPresentationRepository = mock(UserPresentationRepository.class);
        employeePreferWorkdayBoardService = mock(EmployeePreferWorkdayBoardService.class);
        userService = mock(UserService.class);

        shiftArrangementService = new ShiftArrangementService();
        shiftArrangementService.shiftArrangementRepository = shiftArrangementRepository;
        shiftArrangementService.userRepository = userRepository;
        shiftArrangementService.userPresentationRepository = userPresentationRepository;
        shiftArrangementService.employeePreferWorkdayBoardService = employeePreferWorkdayBoardService;
        shiftArrangementService.userService = userService;
    }

    @Test
    void candidatesByDateMarksPreferredAndAlreadyScheduledEmployees() {
        ZonedDateTime selected = ZonedDateTime.of(2026, 5, 21, 22, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime dayStart = ZonedDateTime.of(2026, 5, 21, 0, 0, 0, 0, ZoneId.of("America/Vancouver"));
        ZonedDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
        when(userPresentationRepository.findByRolesContainingAndActiveOrderByNameAsc("tester", 1))
                .thenReturn(List.of(
                        employee("zoe", null, "surrey"),
                        employee("alice", "Alice Chen", "surrey"),
                        employee("bob", "Bob Lee", "coquitlam")
                ));
        when(employeePreferWorkdayBoardService.getPreferredEmployeesBydate(selected))
                .thenReturn(List.of("alice", "bob"));
        when(shiftArrangementRepository.getShiftArrangementDOByStartBetween(dayStart, dayEnd))
                .thenReturn(List.of(
                        shift(9, "bob", "active", vancouverTime(2026, 5, 21, 14)),
                        shift(3, "bob", "paid_sick_leave", vancouverTime(2026, 5, 21, 9))
                ));

        List<ShiftCandidateDTO> candidates = shiftArrangementService.getCandidatesByDate(selected, "surrey", "tester");

        assertEquals(3, candidates.size());
        assertEquals("alice", candidates.get(0).getUsername());
        assertEquals("Alice Chen", candidates.get(0).getName());
        assertTrue(candidates.get(0).isPreferred());
        assertFalse(candidates.get(0).isAlreadyScheduled());
        assertEquals("bob", candidates.get(1).getUsername());
        assertTrue(candidates.get(1).isPreferred());
        assertTrue(candidates.get(1).isAlreadyScheduled());
        assertEquals(3, candidates.get(1).getExistingShiftId());
        assertEquals("paid_sick_leave", candidates.get(1).getExistingShiftStatus());
        assertEquals("zoe", candidates.get(2).getUsername());
        assertEquals("zoe", candidates.get(2).getName());
        assertFalse(candidates.get(2).isPreferred());
        assertFalse(candidates.get(2).isAlreadyScheduled());
    }

    @Test
    void managerCanMarkPaidSickLeaveWhenQuotaHasRoom() {
        ShiftArrangementDO shift = shift(7, "employee", "active", vancouverTime(2026, 5, 13, 9));
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(shift));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(manager());
        when(userService.isInProbation("employee")).thenReturn(false);
        when(shiftArrangementRepository.getShiftArrangementDOByUsernameAndStatusAndStartBetween(
                eq("employee"), eq("paid_sick_leave"), any(), any()
        )).thenReturn(List.of(
                shift(1, "employee", "paid_sick_leave", vancouverTime(2026, 1, 2, 9)),
                shift(2, "employee", "paid_sick_leave", vancouverTime(2026, 1, 2, 13))
        ));
        when(shiftArrangementRepository.save(any(ShiftArrangementDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftArrangement updated = shiftArrangementService.updateStatus(7, "paid_sick_leave", "manager");

        assertEquals("paid_sick_leave", updated.getStatus());
        ArgumentCaptor<ShiftArrangementDO> captor = ArgumentCaptor.forClass(ShiftArrangementDO.class);
        verify(shiftArrangementRepository).save(captor.capture());
        assertEquals("paid_sick_leave", captor.getValue().getStatus());
    }

    @Test
    void managerCanMarkPersonalLeaveWithoutPaidSickLeaveQuotaCheck() {
        ShiftArrangementDO shift = shift(7, "employee", "active", vancouverTime(2026, 5, 13, 9));
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(shift));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(manager());
        when(shiftArrangementRepository.save(any(ShiftArrangementDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftArrangement updated = shiftArrangementService.updateStatus(7, "personal_leave", "manager");

        assertEquals("personal_leave", updated.getStatus());
        ArgumentCaptor<ShiftArrangementDO> captor = ArgumentCaptor.forClass(ShiftArrangementDO.class);
        verify(shiftArrangementRepository).save(captor.capture());
        assertEquals("personal_leave", captor.getValue().getStatus());
        verify(userService, never()).isInProbation("employee");
        verify(shiftArrangementRepository, never()).getShiftArrangementDOByUsernameAndStatusAndStartBetween(
                eq("employee"), eq("paid_sick_leave"), any(), any()
        );
    }

    @Test
    void activeStatusIsRejectedByManualStatusApi() {
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(shift(7, "employee", "active", vancouverTime(2026, 5, 13, 9))));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(manager());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> shiftArrangementService.updateStatus(7, "active", "manager"));

        assertEquals("Invalid shift status target: active", exception.getMessage());
    }

    @Test
    void modifyArrangementPreservesExistingStatusWhenDtoDoesNotSupplyStatus() {
        ShiftArrangement existing = ShiftArrangement.fromDO(shift(7, "employee", "paid_sick_leave", vancouverTime(2026, 5, 13, 9)));
        ShiftArrangement incoming = ShiftArrangement.fromDO(shift(7, "employee", null, vancouverTime(2026, 5, 13, 10)));
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(existing.getDO()));
        when(shiftArrangementRepository.save(any(ShiftArrangementDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftArrangement modified = shiftArrangementService.modifyArrangement(incoming);

        assertEquals("paid_sick_leave", modified.getStatus());
    }

    @Test
    void paidSickLeaveIsRejectedForProbationEmployee() {
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(shift(7, "employee", "active", vancouverTime(2026, 5, 13, 9))));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(manager());
        when(userService.isInProbation("employee")).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> shiftArrangementService.updateStatus(7, "paid_sick_leave", "manager"));

        assertEquals("Employee is still in probation", exception.getMessage());
    }

    @Test
    void quotaCountsDistinctVancouverCalendarDaysAndAllowsSameDayWhenLimitReached() {
        ShiftArrangementDO target = shift(7, "employee", "active", vancouverTime(2026, 2, 3, 14));
        when(shiftArrangementRepository.findById(7)).thenReturn(Optional.of(target));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(manager());
        when(userService.isInProbation("employee")).thenReturn(false);
        when(shiftArrangementRepository.getShiftArrangementDOByUsernameAndStatusAndStartBetween(
                eq("employee"), eq("paid_sick_leave"), any(), any()
        )).thenReturn(List.of(
                shift(1, "employee", "paid_sick_leave", vancouverTime(2026, 1, 1, 9)),
                shift(2, "employee", "paid_sick_leave", vancouverTime(2026, 1, 2, 9)),
                shift(3, "employee", "paid_sick_leave", vancouverTime(2026, 1, 3, 9)),
                shift(4, "employee", "paid_sick_leave", vancouverTime(2026, 1, 4, 9)),
                shift(5, "employee", "paid_sick_leave", vancouverTime(2026, 2, 3, 9))
        ));

        PaidSickLeaveQuotaDTO quota = shiftArrangementService.getPaidSickLeaveQuota(7, "manager");

        assertEquals(2026, quota.getYear());
        assertEquals(5, quota.getUsedDays());
        assertTrue(quota.isTargetDateAlreadyCounted());
        assertTrue(quota.isCanMarkPaidSickLeave());
        assertFalse(quota.isProbation());
    }

    private ShiftArrangementDO shift(Integer id, String username, String status, ZonedDateTime start) {
        ShiftArrangementDO shift = new ShiftArrangementDO();
        shift.setId(id);
        shift.setUsername(username);
        shift.setStatus(status);
        shift.setStart(start);
        shift.setEnd(start.plusHours(8));
        shift.setGroup("surrey");
        return shift;
    }

    private UserDO manager() {
        UserDO user = new UserDO();
        user.setUsername("manager");
        user.setRoles("Manager");
        user.setGroupName("manager");
        user.setActive(1);
        return user;
    }

    private UserPresentation employee(String username, String name, String groupName) {
        UserPresentation user = new UserPresentation();
        user.setUsername(username);
        user.setName(name);
        user.setGroupName(groupName);
        user.setRoles("tester");
        user.setActive(1);
        return user;
    }

    private ZonedDateTime vancouverTime(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneId.of("America/Vancouver"))
                .withZoneSameInstant(ZoneId.of("UTC"));
    }
}
