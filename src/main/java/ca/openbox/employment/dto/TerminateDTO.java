package ca.openbox.employment.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TerminateDTO {
    private LocalDate lastDay;
    private String terminationReason;
}
