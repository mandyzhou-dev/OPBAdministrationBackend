package ca.openbox.process.service.components;

import ca.openbox.process.entities.LeaveApplication;
import lombok.Data;

@Data
public class LeaveApplicationEmailEvent {
    private LeaveApplicationEmailEventType type;
    private LeaveApplication leaveApplication;
    private String sickProofStoredPath;

    private LeaveApplicationEmailEvent(LeaveApplicationEmailEventType type, LeaveApplication leaveApplication, String sickProofStoredPath) {
        this.type = type;
        this.leaveApplication = leaveApplication;
        this.sickProofStoredPath = sickProofStoredPath;
    }

    public static LeaveApplicationEmailEvent leaveSubmitted(LeaveApplication leaveApplication) {
        return new LeaveApplicationEmailEvent(LeaveApplicationEmailEventType.LEAVE_SUBMITTED, leaveApplication, null);
    }

    public static LeaveApplicationEmailEvent sickProofUploaded(LeaveApplication leaveApplication) {
        return sickProofUploaded(leaveApplication, null);
    }

    public static LeaveApplicationEmailEvent sickProofUploaded(LeaveApplication leaveApplication, String sickProofStoredPath) {
        return new LeaveApplicationEmailEvent(LeaveApplicationEmailEventType.SICK_PROOF_UPLOADED, leaveApplication, sickProofStoredPath);
    }
}
