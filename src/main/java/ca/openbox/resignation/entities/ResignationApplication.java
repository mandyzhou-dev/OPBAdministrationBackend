package ca.openbox.resignation.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Entity
@Table(name="opb_resignations")
public class ResignationApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String applicant;
    private LocalDate lastWorkingDay;
    private String reason;
    private ZonedDateTime submittedAt;
    @Enumerated(EnumType.STRING)
    private ResignationApplicationStatus status = ResignationApplicationStatus.PENDING_REVIEW;         // PENDING_REVIEW / REVIEWED
    private String reviewedBy;
    private String note;
}
