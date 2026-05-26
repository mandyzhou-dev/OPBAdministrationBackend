package ca.openbox.shift.service.copy;

import ca.openbox.shift.dataobject.StatutoryHolidayDO;
import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dto.PresetRequestDTO;
import ca.openbox.shift.dto.PresetResultDTO;
import ca.openbox.shift.dto.PresetSkippedShiftDTO;
import ca.openbox.shift.exception.InvalidScheduleRangeException;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.shift.repository.StatutoryHolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeekScheduleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");
    private static final String STATUTORY_HOLIDAY_REASON = "STATUTORY_HOLIDAY";

    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;

    @Autowired
    StatutoryHolidayRepository statutoryHolidayRepository;

    @Transactional
    public PresetResultDTO copyWeekSchedule(PresetRequestDTO presetRequestDTO) {


        //1.Load Source Week Shifts
        //1.1 Global time zone handling
        ZonedDateTime srcStart = presetRequestDTO.getSrcWeekStart()
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime srcEnd = srcStart.plusDays(7);//[start, end)

        ZonedDateTime dstStart = presetRequestDTO.getTgtWeekStart()
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime dstEnd = dstStart.plusDays(7);

        //2. Validate Request
        //2.1 TODO：Validate srcWeekStart,tgtWeekStart, groupName, mode
        validateRequest(presetRequestDTO,dstStart, dstEnd);

        //2.2 Query all shifts for the specified group
        List<ShiftArrangementDO> shiftArrangementDOList =
                shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween(
                        presetRequestDTO.getGroupName(), srcStart, srcEnd
                );
        //3. Calculate Time Offset
        //- offsetDays = targetWeekStart - sourceWeekStart
        long offsetDays = ChronoUnit.DAYS.between(presetRequestDTO.getSrcWeekStart(),
                presetRequestDTO.getTgtWeekStart());
        Map<LocalDate, String> holidayNamesByDate = new HashMap<>();
        for (StatutoryHolidayDO holiday : statutoryHolidayRepository.findByStatutoryDateBetween(
                presetRequestDTO.getTgtWeekStart(),
                presetRequestDTO.getTgtWeekStart().plusDays(6)
        )) {
            holidayNamesByDate.putIfAbsent(holiday.getStatutoryDate(), holiday.getHolidayName());
        }
        // 4. Generate Candidate Shifts: For each shift in the source week:
        //    - create a new shift candidate:  newShiftDate = originalShiftDate + offsetDays
        List<ShiftArrangementDO> generatedShiftDOs = new ArrayList<>();
        List<PresetSkippedShiftDTO> skippedDetails = new ArrayList<>();
        for (ShiftArrangementDO shiftArrangementDO : shiftArrangementDOList) {
            ShiftArrangementDO generatedShiftDO = new ShiftArrangementDO();
            generatedShiftDO.setStart(
                    shiftArrangementDO.getStart().withZoneSameInstant(BUSINESS_ZONE).
                            plusDays(offsetDays).withZoneSameInstant(ZoneOffset.UTC)
            );
            generatedShiftDO.setEnd(
                    shiftArrangementDO.getEnd().withZoneSameInstant(BUSINESS_ZONE).
                            plusDays(offsetDays).withZoneSameInstant(ZoneOffset.UTC)
            );
            generatedShiftDO.setUsername(shiftArrangementDO.getUsername());
            generatedShiftDO.setStatus(shiftArrangementDO.getStatus());
            generatedShiftDO.setGroup(shiftArrangementDO.getGroup());
            LocalDate targetBusinessDate = generatedShiftDO.getStart()
                    .withZoneSameInstant(BUSINESS_ZONE)
                    .toLocalDate();
            if (holidayNamesByDate.containsKey(targetBusinessDate)) {
                skippedDetails.add(buildSkippedShiftDetail(
                        shiftArrangementDO,
                        targetBusinessDate,
                        holidayNamesByDate.get(targetBusinessDate)
                ));
                continue;
            }
            generatedShiftDOs.add(generatedShiftDO);
        }
        // TODO: 5. Detect Conflicts
        //TODO: 6. Apply Conflict strategy: skip/overwrite
        //7. insert shifts
        if (!generatedShiftDOs.isEmpty()) {
            shiftArrangementRepository.saveAll(generatedShiftDOs);
        }

        // 8. Build result
        PresetResultDTO result = new PresetResultDTO();
        result.setCreated(generatedShiftDOs.size());
        result.setSkipped(skippedDetails.size());
        result.setOverwritten(0);
        result.setSkippedDetails(skippedDetails);
        // 9. Return
        return result;
    }

    private PresetSkippedShiftDTO buildSkippedShiftDetail(
            ShiftArrangementDO sourceShift,
            LocalDate targetBusinessDate,
            String holidayName
    ) {
        PresetSkippedShiftDTO detail = new PresetSkippedShiftDTO();
        detail.setUsername(sourceShift.getUsername());
        detail.setGroupName(sourceShift.getGroup());
        detail.setSourceDate(sourceShift.getStart().withZoneSameInstant(BUSINESS_ZONE).toLocalDate());
        detail.setTargetDate(targetBusinessDate);
        detail.setReason(STATUTORY_HOLIDAY_REASON);
        detail.setMessage(buildHolidaySkipMessage(targetBusinessDate, holidayName));
        return detail;
    }

    private String buildHolidaySkipMessage(LocalDate targetBusinessDate, String holidayName) {
        if (holidayName == null || holidayName.isBlank()) {
            return "Skipped because " + targetBusinessDate + " is a statutory holiday.";
        }
        return "Skipped because " + targetBusinessDate + " is a statutory holiday: " + holidayName + ".";
    }

    private void validateRequest(PresetRequestDTO dto,ZonedDateTime dstStart, ZonedDateTime dstEnd) {
        if (dto.getTgtWeekStart().isBefore(dto.getSrcWeekStart())) {
            throw new InvalidScheduleRangeException("Target week cannot be earlier than source");
        }
        if (!shiftArrangementRepository
                .getShiftArrangementDOByGroupAndStartBetween(
                        dto.getGroupName(),
                        dstStart,
                        dstEnd).isEmpty()) {
            throw new DuplicateKeyException("Target week already has schedules!");
        }
    }
}
