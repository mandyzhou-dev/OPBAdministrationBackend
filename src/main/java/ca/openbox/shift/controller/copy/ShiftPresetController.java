package ca.openbox.shift.controller.copy;

import ca.openbox.shift.dto.PresetRequestDTO;
import ca.openbox.shift.dto.PresetResultDTO;
import ca.openbox.shift.service.copy.WeekScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/shift/preset")
public class ShiftPresetController {
        @Autowired
        private WeekScheduleService weekScheduleService;

        @CrossOrigin(origins = "http://localhost:8081")
        @PostMapping
        public PresetResultDTO copyWeekSchedule(@RequestBody PresetRequestDTO presetRequestDTO) {
            return weekScheduleService.copyWeekSchedule(presetRequestDTO);
        }
}
