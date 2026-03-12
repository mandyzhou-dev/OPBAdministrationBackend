package ca.openbox.shift.dto;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
@Data
public class BatchCreateShiftByDateDTO {
    ZonedDateTime workDate;
    String groupName;
    List<String> usernames;
}
