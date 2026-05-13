package ca.openbox.shift.dto;

import lombok.Data;

@Data
public class PaidSickLeaveQuotaDTO {
    private String username;
    private int year;
    private int usedDays;
    private int quotaDays;
    private boolean probation;
    private boolean eligible;
    private boolean targetDateAlreadyCounted;
    private boolean canMarkPaidSickLeave;
    private String message;
}
