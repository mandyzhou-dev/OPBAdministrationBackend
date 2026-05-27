package ca.openbox.process.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LeaveDateAvailabilityDateDTO {
    private String date;
    private boolean scheduled;
    private List<Integer> shiftIds;
}
