package ca.openbox.process.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Entity
@Table(name = "opb_leave_application_proof")
public class LeaveApplicationProofDO {
    @Id
    @Column(name = "application_id")
    private Integer applicationId;
    private String proofType = "SICK_LEAVE_PROOF";
    private String status = "REQUIRED";
    private ZonedDateTime uploadedAt;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private Long fileSizeBytes;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
