package ca.openbox.shift.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PresetSkippedShiftDTO {
    private String username;
    private String groupName;
    private LocalDate sourceDate;
    private LocalDate targetDate;
    private String reason;
    private String message;
}
