package ca.openbox.shift.service.copy;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dto.PresetRequestDTO;
import ca.openbox.shift.dto.PresetResultDTO;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.exception.InvalidScheduleRangeException;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeekScheduleService {
    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;

    @Transactional
    public PresetResultDTO copyWeekSchedule(PresetRequestDTO presetRequestDTO) {


        //1.Load Source Week Shifts
        //1.1 Global time zone handling
        ZoneId zone = ZoneId.of("America/Vancouver");

        ZonedDateTime srcStart = presetRequestDTO.getSrcWeekStart()
                .atStartOfDay(zone)
                .withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime srcEnd = srcStart.plusDays(7);//[start, end)

        ZonedDateTime dstStart = presetRequestDTO.getTgtWeekStart()
                .atStartOfDay(zone)
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
        // 4. Generate Candidate Shifts: For each shift in the source week:
        //    - create a new shift candidate:  newShiftDate = originalShiftDate + offsetDays
        List<ShiftArrangementDO> generatedShiftDOs = new ArrayList<>();
        for (ShiftArrangementDO shiftArrangementDO : shiftArrangementDOList) {
            ShiftArrangementDO generatedShiftDO = new ShiftArrangementDO();
            generatedShiftDO.setStart(
                    shiftArrangementDO.getStart().withZoneSameInstant(zone).
                            plusDays(offsetDays).withZoneSameInstant(ZoneOffset.UTC)
            );
            generatedShiftDO.setEnd(
                    shiftArrangementDO.getEnd().withZoneSameInstant(zone).
                            plusDays(offsetDays).withZoneSameInstant(ZoneOffset.UTC)
            );
            generatedShiftDO.setUsername(shiftArrangementDO.getUsername());
            generatedShiftDO.setStatus(shiftArrangementDO.getStatus());
            generatedShiftDO.setGroup(shiftArrangementDO.getGroup());
            generatedShiftDOs.add(generatedShiftDO);
        }
        // TODO: 5. Detect Conflicts
        //TODO: 6. Apply Conflict strategy: skip/overwrite
        //7. insert shifts
        shiftArrangementRepository.saveAll(generatedShiftDOs);

        // 8. Build result
        PresetResultDTO result = new PresetResultDTO();
        result.setCreated(generatedShiftDOs.size());
        result.setSkipped(0);
        result.setOverwritten(0);
        // 9. Return
        return result;
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
