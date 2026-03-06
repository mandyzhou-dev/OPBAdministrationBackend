package ca.openbox.shift.dto;

import ca.openbox.shift.entities.ConflictReason;
import lombok.Data;

import java.time.LocalDate;
@Data
public class ScheduleConflictDetails {
    String username;
    ConflictReason reason;
    Integer existingShiftID;
    LocalDate requestedDate;
}
