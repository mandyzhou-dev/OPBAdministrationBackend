package ca.openbox.shift.dataobject;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;


import java.time.ZonedDateTime;

@Data
@Entity
@DynamicUpdate
@DynamicInsert
@Table(name = "opb_shift_arrangement")
@Audited
public class ShiftArrangementDO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String username;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private String status;
    @Column(name = "group_name")
    private String group;
}

