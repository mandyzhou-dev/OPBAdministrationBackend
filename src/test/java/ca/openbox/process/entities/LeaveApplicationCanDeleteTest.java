package ca.openbox.process.entities;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaveApplicationCanDeleteTest {

    @Test
    void fromDODerivesCanDeleteForPendingAndDraftStatuses() {
        assertTrue(LeaveApplication.fromDO(application(" pending ")).isCanDelete());
        assertTrue(LeaveApplication.fromDO(application("DRAFT")).isCanDelete());
    }

    @Test
    void fromDODerivesCannotDeleteForTerminalAndUnknownStatuses() {
        assertFalse(LeaveApplication.fromDO(application("approved")).isCanDelete());
        assertFalse(LeaveApplication.fromDO(application("rejected")).isCanDelete());
        assertFalse(LeaveApplication.fromDO(application("cancelled")).isCanDelete());
        assertFalse(LeaveApplication.fromDO(application("unknown")).isCanDelete());
        assertFalse(LeaveApplication.fromDO(application(" ")).isCanDelete());
        assertFalse(LeaveApplication.fromDO(application(null)).isCanDelete());
    }

    private LeaveApplicationDO application(String status) {
        LeaveApplicationDO application = new LeaveApplicationDO();
        application.setStatus(status);
        return application;
    }
}
