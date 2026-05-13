package ca.openbox.shift.dto;

import lombok.Data;

@Data
public class ShiftStatusUpdateDTO {
    private String status;
    private String operatorUsername;
}
