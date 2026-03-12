package ca.openbox.shift.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Data
public class ShiftArrangementDTO {
    private Integer id;
    private String username;
    private ZonedDateTime start;
    private ZonedDateTime end;

    private String status;
    private String groupName;

}
