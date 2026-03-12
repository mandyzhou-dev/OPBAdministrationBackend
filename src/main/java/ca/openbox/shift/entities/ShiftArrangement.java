package ca.openbox.shift.entities;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dto.ShiftArrangementDTO;
import lombok.Data;
import lombok.Setter;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
@Data
public class ShiftArrangement {
    private Integer id;
    private String username;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private String status;
    private String groupName;

    public Integer workMinutes(){
        Duration duration = Duration.between(getStart(), getEnd());
        if(duration.toMinutes()>300){//5hours on work up minus lunch
            duration= duration.minusMinutes(30);
        }
        return Integer.valueOf((int) duration.toMinutes());
    }
    public ShiftArrangementDO getDO(){
        ShiftArrangementDO shiftArrangementDO = new ShiftArrangementDO();
        shiftArrangementDO.setId(id);
        shiftArrangementDO.setUsername(username);
        shiftArrangementDO.setStart(start);
        shiftArrangementDO.setEnd(end);
        shiftArrangementDO.setStatus(status);
        shiftArrangementDO.setGroup(groupName);
        return shiftArrangementDO;
    }

    static public ShiftArrangement fromDO(ShiftArrangementDO shiftArrangementDO){
        ShiftArrangement shiftArrangement = new ShiftArrangement();
        shiftArrangement.id = shiftArrangementDO.getId();
        shiftArrangement.username =shiftArrangementDO.getUsername();
        shiftArrangement.start = shiftArrangementDO.getStart();
        shiftArrangement.end = shiftArrangementDO.getEnd();
        shiftArrangement.status = shiftArrangementDO.getStatus();
        shiftArrangement.groupName = shiftArrangementDO.getGroup();
        return shiftArrangement;
    }

    static public ShiftArrangement fromDTO(ShiftArrangementDTO shiftArrangementDTO){
        ShiftArrangement shiftArrangement = new ShiftArrangement();
        shiftArrangement.id = shiftArrangementDTO.getId();
        shiftArrangement.start = shiftArrangementDTO.getStart();
        shiftArrangement.end = shiftArrangementDTO.getEnd();
        shiftArrangement.username = shiftArrangementDTO.getUsername();
        shiftArrangement.status = shiftArrangementDTO.getStatus();
        shiftArrangement.groupName = shiftArrangementDTO.getGroupName();
        return shiftArrangement;
    }
}
