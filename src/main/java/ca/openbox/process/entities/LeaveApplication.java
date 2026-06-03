package ca.openbox.process.entities;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dataobject.LeaveApplicationProofDO;
import lombok.Data;

import java.time.ZonedDateTime;
@Data
public class LeaveApplication {
    private Integer id;
    private String applicant;
    private String leaveType;
    private ZonedDateTime submitTime;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private String currentHandler;
    private String status;
    private String reason;
    private String rejectReason;
    private String note;
    private boolean canDelete;
    private boolean sickProofRequired;
    private boolean sickProofSubmitted;
    private ZonedDateTime sickProofUploadedAt;
    private String sickProofOriginalFilename;

    static public LeaveApplication fromDO(LeaveApplicationDO leaveApplicationDO){
        LeaveApplication leaveApplication = new LeaveApplication();
        leaveApplication.id = leaveApplicationDO.getId();
        leaveApplication.applicant = leaveApplicationDO.getApplicant();
        leaveApplication.currentHandler = leaveApplicationDO.getCurrentHandler();
        leaveApplication.status = leaveApplicationDO.getStatus();
        leaveApplication.leaveType = leaveApplicationDO.getLeaveType();
        leaveApplication.start = leaveApplicationDO.getStart();
        leaveApplication.end= leaveApplicationDO.getEnd();
        leaveApplication.submitTime = leaveApplicationDO.getSubmitTime();
        leaveApplication.rejectReason = leaveApplicationDO.getRejectReason();
        leaveApplication.reason=leaveApplicationDO.getReason();
        leaveApplication.note = leaveApplicationDO.getNote();
        leaveApplication.canDelete = LeaveApplicationDeleteRules.canDeleteStatus(leaveApplicationDO.getStatus());
        leaveApplication.sickProofRequired = isSickLeave(leaveApplicationDO.getLeaveType());
        leaveApplication.sickProofSubmitted = false;
        return leaveApplication;
    }

    static public LeaveApplication fromDO(LeaveApplicationDO leaveApplicationDO, LeaveApplicationProofDO proofDO) {
        LeaveApplication leaveApplication = fromDO(leaveApplicationDO);
        leaveApplication.applyProof(proofDO);
        return leaveApplication;
    }

    public void applyProof(LeaveApplicationProofDO proofDO) {
        this.sickProofRequired = isSickLeave(this.leaveType);
        this.sickProofSubmitted = proofDO != null && "SUBMITTED".equalsIgnoreCase(proofDO.getStatus());
        this.sickProofUploadedAt = proofDO == null ? null : proofDO.getUploadedAt();
        this.sickProofOriginalFilename = proofDO == null ? null : proofDO.getOriginalFilename();
    }

    public LeaveApplicationDO toDO(){
        LeaveApplicationDO leaveApplicationDO = new LeaveApplicationDO();
        leaveApplicationDO.setId(id);
        leaveApplicationDO.setSubmitTime(submitTime);
        leaveApplicationDO.setApplicant(applicant);
        leaveApplicationDO.setStatus(status);
        leaveApplicationDO.setLeaveType(leaveType);
        leaveApplicationDO.setStart(start);
        leaveApplicationDO.setEnd(end);
        leaveApplicationDO.setCurrentHandler(currentHandler);
        leaveApplicationDO.setReason(reason);
        leaveApplicationDO.setRejectReason(rejectReason);
        leaveApplicationDO.setNote(note);
        return leaveApplicationDO;

    }

    private static boolean isSickLeave(String leaveType) {
        return leaveType != null && "SICK".equalsIgnoreCase(leaveType.trim());
    }

}
