package ca.openbox.shift.repository;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.entities.ShiftArrangement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

import java.time.ZonedDateTime;
import java.util.List;

public interface ShiftArrangementRepository extends JpaRepository<ShiftArrangementDO, Integer> {
    List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndStartBetween(String username, ZonedDateTime left, ZonedDateTime right);
    List<ShiftArrangementDO> getShiftArrangementDOByGroupAndStartBetween(String groupName, ZonedDateTime start, ZonedDateTime end);
    List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndGroupAndStartBetween(String username, String groupName, ZonedDateTime start, ZonedDateTime end);
}
