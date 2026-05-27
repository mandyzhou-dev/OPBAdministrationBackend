package ca.openbox.process.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LeaveDateAvailabilityDTO {
    private String applicant;
    private String from;
    private String to;
    private String businessZone;
    private List<LeaveDateAvailabilityDateDTO> dates;
}
