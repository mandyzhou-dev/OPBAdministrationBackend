package ca.openbox.shift.dto;

import ca.openbox.shift.entities.PresetMode;
import lombok.Data;

import java.time.LocalDate;
import java.time.ZonedDateTime;
@Data
public class PresetRequestDTO {
    private String groupName;
    private LocalDate srcWeekStart;
    private LocalDate tgtWeekStart;
    private PresetMode mode;
}
