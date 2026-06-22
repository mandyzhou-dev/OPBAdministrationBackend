package ca.openbox.shift.entities;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ShiftStatus {
    ACTIVE("active", false, false),
    CANCELLED("cancelled", false, true),
    NO_SHOW("no_show", true, true),
    PAID_SICK_LEAVE("paid_sick_leave", true, true),
    UNPAID_SICK_LEAVE("unpaid_sick_leave", true, true),
    PERSONAL_LEAVE("personal_leave", true, true);

    public static final String PAID_SICK_LEAVE_VALUE = "paid_sick_leave";

    private static final Set<String> MANUAL_TARGETS = Arrays.stream(values())
            .filter(ShiftStatus::isManualTarget)
            .map(ShiftStatus::getValue)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> NON_WORKED_STATUSES = Arrays.stream(values())
            .filter(ShiftStatus::isNonWorked)
            .map(ShiftStatus::getValue)
            .collect(Collectors.toUnmodifiableSet());

    private final String value;
    private final boolean manualTarget;
    private final boolean nonWorked;

    ShiftStatus(String value, boolean manualTarget, boolean nonWorked) {
        this.value = value;
        this.manualTarget = manualTarget;
        this.nonWorked = nonWorked;
    }

    public String getValue() {
        return value;
    }

    public boolean isManualTarget() {
        return manualTarget;
    }

    public boolean isNonWorked() {
        return nonWorked;
    }

    public static boolean isAllowedManualTarget(String value) {
        return value != null && MANUAL_TARGETS.contains(value);
    }

    public static boolean isNonWorked(String value) {
        return value != null && NON_WORKED_STATUSES.contains(value);
    }

    public static Set<String> nonWorkedValues() {
        return NON_WORKED_STATUSES;
    }
}
