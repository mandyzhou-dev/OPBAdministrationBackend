package ca.openbox.shift.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftStatusTest {

    @Test
    void personalLeaveAndExistingLeaveStatusesAreManualTargets() {
        assertTrue(ShiftStatus.isAllowedManualTarget("no_show"));
        assertTrue(ShiftStatus.isAllowedManualTarget("paid_sick_leave"));
        assertTrue(ShiftStatus.isAllowedManualTarget("unpaid_sick_leave"));
        assertTrue(ShiftStatus.isAllowedManualTarget("personal_leave"));

        assertFalse(ShiftStatus.isAllowedManualTarget("active"));
        assertFalse(ShiftStatus.isAllowedManualTarget("cancelled"));
    }

    @Test
    void nonWorkedStatusesIncludeCancelledAndManualLeaveStatuses() {
        assertTrue(ShiftStatus.isNonWorked("cancelled"));
        assertTrue(ShiftStatus.isNonWorked("no_show"));
        assertTrue(ShiftStatus.isNonWorked("paid_sick_leave"));
        assertTrue(ShiftStatus.isNonWorked("unpaid_sick_leave"));
        assertTrue(ShiftStatus.isNonWorked("personal_leave"));

        assertFalse(ShiftStatus.isNonWorked("active"));
        assertFalse(ShiftStatus.isNonWorked(null));
    }
}
