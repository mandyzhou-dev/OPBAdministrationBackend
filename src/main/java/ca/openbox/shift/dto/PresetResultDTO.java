package ca.openbox.shift.dto;

import lombok.Data;

import java.util.List;

@Data
public class PresetResultDTO {
    Integer created;
    Integer skipped;
    Integer overwritten;
    List<PresetSkippedShiftDTO> skippedDetails;
}
