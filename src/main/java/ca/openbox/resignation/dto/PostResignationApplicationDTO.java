package ca.openbox.resignation.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
public class PostResignationApplicationDTO {
    private String applicant;
    private String reason;
    private LocalDate lastWorkingDate;
    private ZonedDateTime submittedAt;
}
