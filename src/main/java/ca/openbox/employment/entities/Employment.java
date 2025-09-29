package ca.openbox.employment.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name="opb_employment_record")
public class Employment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String roles;
    private String username;
    private String legalName;
    private LocalDate bigDay;
    private LocalDate lastDay;
    private LocalDate noticeDate;
    private String terminationReason;
}
