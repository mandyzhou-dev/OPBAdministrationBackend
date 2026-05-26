package ca.openbox.shift.service.copy;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dataobject.StatutoryHolidayDO;
import ca.openbox.shift.dto.PresetRequestDTO;
import ca.openbox.shift.dto.PresetResultDTO;
import ca.openbox.shift.entities.PresetMode;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.shift.repository.StatutoryHolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeekScheduleServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

    private ShiftArrangementRepository shiftArrangementRepository;
    private StatutoryHolidayRepository statutoryHolidayRepository;
    private WeekScheduleService weekScheduleService;

    @BeforeEach
    void setUp() {
        shiftArrangementRepository = mock(ShiftArrangementRepository.class);
        statutoryHolidayRepository = mock(StatutoryHolidayRepository.class);

        weekScheduleService = new WeekScheduleService();
        weekScheduleService.shiftArrangementRepository = shiftArrangementRepository;
        ReflectionTestUtils.setField(weekScheduleService, "statutoryHolidayRepository", statutoryHolidayRepository);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void copyWeekScheduleSkipsGeneratedShiftOnTargetStatutoryHoliday() {
        PresetRequestDTO request = presetRequest(LocalDate.of(2026, 5, 17), LocalDate.of(2026, 5, 24));
        ZonedDateTime sourceStart = weekStartUtc(request.getSrcWeekStart());
        ZonedDateTime sourceEnd = sourceStart.plusDays(7);
        ZonedDateTime targetStart = weekStartUtc(request.getTgtWeekStart());
        ZonedDateTime targetEnd = targetStart.plusDays(7);

        ShiftArrangementDO normalCandidate = shift("alice", "surrey", "active", vancouverTime(2026, 5, 18, 9));
        ShiftArrangementDO holidayCandidate = shift("bob", "surrey", "active", vancouverTime(2026, 5, 19, 9));

        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", targetStart, targetEnd))
                .thenReturn(List.of());
        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", sourceStart, sourceEnd))
                .thenReturn(List.of(normalCandidate, holidayCandidate));
        when(statutoryHolidayRepository.findByStatutoryDateBetween(
                request.getTgtWeekStart(), request.getTgtWeekStart().plusDays(6)
        )).thenReturn(List.of(holiday(LocalDate.of(2026, 5, 26), "Holiday Tuesday")));

        PresetResultDTO result = weekScheduleService.copyWeekSchedule(request);

        ArgumentCaptor<Iterable<ShiftArrangementDO>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(shiftArrangementRepository).saveAll(captor.capture());
        List<ShiftArrangementDO> saved = toList(captor.getValue());

        assertEquals(1, saved.size());
        assertEquals("alice", saved.get(0).getUsername());
        assertEquals(LocalDate.of(2026, 5, 25), saved.get(0).getStart().withZoneSameInstant(VANCOUVER).toLocalDate());
        assertEquals(1, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getOverwritten());
        assertNotNull(result.getSkippedDetails());
        assertEquals(1, result.getSkippedDetails().size());
        assertEquals("bob", result.getSkippedDetails().get(0).getUsername());
        assertEquals("surrey", result.getSkippedDetails().get(0).getGroupName());
        assertEquals(LocalDate.of(2026, 5, 19), result.getSkippedDetails().get(0).getSourceDate());
        assertEquals(LocalDate.of(2026, 5, 26), result.getSkippedDetails().get(0).getTargetDate());
        assertEquals("STATUTORY_HOLIDAY", result.getSkippedDetails().get(0).getReason());
    }

    @Test
    void copyWeekScheduleDoesNotSaveWhenEveryGeneratedShiftTargetsAStatutoryHoliday() {
        PresetRequestDTO request = presetRequest(LocalDate.of(2026, 5, 17), LocalDate.of(2026, 5, 24));
        ZonedDateTime sourceStart = weekStartUtc(request.getSrcWeekStart());
        ZonedDateTime sourceEnd = sourceStart.plusDays(7);
        ZonedDateTime targetStart = weekStartUtc(request.getTgtWeekStart());
        ZonedDateTime targetEnd = targetStart.plusDays(7);

        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", targetStart, targetEnd))
                .thenReturn(List.of());
        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", sourceStart, sourceEnd))
                .thenReturn(List.of(shift("alice", "surrey", "active", vancouverTime(2026, 5, 19, 9))));
        when(statutoryHolidayRepository.findByStatutoryDateBetween(
                request.getTgtWeekStart(), request.getTgtWeekStart().plusDays(6)
        )).thenReturn(List.of(holiday(LocalDate.of(2026, 5, 26), "Holiday Tuesday")));

        PresetResultDTO result = weekScheduleService.copyWeekSchedule(request);

        verify(shiftArrangementRepository, never()).saveAll(any());
        assertEquals(0, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getOverwritten());
        assertEquals(LocalDate.of(2026, 5, 26), result.getSkippedDetails().get(0).getTargetDate());
    }

    @Test
    void copyWeekScheduleUsesVancouverBusinessDateForHolidaySkips() {
        PresetRequestDTO request = presetRequest(LocalDate.of(2026, 5, 17), LocalDate.of(2026, 5, 24));
        ZonedDateTime sourceStart = weekStartUtc(request.getSrcWeekStart());
        ZonedDateTime sourceEnd = sourceStart.plusDays(7);
        ZonedDateTime targetStart = weekStartUtc(request.getTgtWeekStart());
        ZonedDateTime targetEnd = targetStart.plusDays(7);

        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", targetStart, targetEnd))
                .thenReturn(List.of());
        when(shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween("surrey", sourceStart, sourceEnd))
                .thenReturn(List.of(shift("alice", "surrey", "active", vancouverTime(2026, 5, 18, 23))));
        when(statutoryHolidayRepository.findByStatutoryDateBetween(
                request.getTgtWeekStart(), request.getTgtWeekStart().plusDays(6)
        )).thenReturn(List.of(holiday(LocalDate.of(2026, 5, 25), "Holiday Monday")));

        PresetResultDTO result = weekScheduleService.copyWeekSchedule(request);

        verify(shiftArrangementRepository, never()).saveAll(any());
        assertEquals(0, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(LocalDate.of(2026, 5, 25), result.getSkippedDetails().get(0).getTargetDate());
    }

    private PresetRequestDTO presetRequest(LocalDate sourceWeekStart, LocalDate targetWeekStart) {
        PresetRequestDTO request = new PresetRequestDTO();
        request.setGroupName("surrey");
        request.setSrcWeekStart(sourceWeekStart);
        request.setTgtWeekStart(targetWeekStart);
        request.setMode(PresetMode.SKIP);
        return request;
    }

    private ShiftArrangementDO shift(String username, String groupName, String status, ZonedDateTime start) {
        ShiftArrangementDO shift = new ShiftArrangementDO();
        shift.setUsername(username);
        shift.setGroup(groupName);
        shift.setStatus(status);
        shift.setStart(start);
        shift.setEnd(start.plusHours(8));
        return shift;
    }

    private StatutoryHolidayDO holiday(LocalDate date, String name) {
        StatutoryHolidayDO holiday = new StatutoryHolidayDO();
        holiday.setStatutoryDate(date);
        holiday.setHolidayName(name);
        return holiday;
    }

    private ZonedDateTime weekStartUtc(LocalDate date) {
        return date.atStartOfDay(VANCOUVER).withZoneSameInstant(ZoneOffset.UTC);
    }

    private ZonedDateTime vancouverTime(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, VANCOUVER)
                .withZoneSameInstant(ZoneOffset.UTC);
    }

    private List<ShiftArrangementDO> toList(Iterable<ShiftArrangementDO> shifts) {
        List<ShiftArrangementDO> result = new java.util.ArrayList<>();
        for (ShiftArrangementDO shift : shifts) {
            result.add(shift);
        }
        return result;
    }
}
