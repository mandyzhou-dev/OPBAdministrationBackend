package ca.openbox.shift.presentation;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;


import java.time.ZonedDateTime;

@Data
@Entity
public class ShiftPresentation {
    @Id
    private Integer id;
    private String username;
    private String userRealName;

    private ZonedDateTime start;
    private ZonedDateTime end;

    private String groupName;
    private String status;
}
