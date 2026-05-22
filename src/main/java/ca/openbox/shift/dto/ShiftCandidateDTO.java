package ca.openbox.shift.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShiftCandidateDTO {
    private String username;
    private String name;
    private String groupName;
    private boolean preferred;
    private boolean alreadyScheduled;
    private Integer existingShiftId;
    private String existingShiftStatus;
}
