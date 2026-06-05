package ca.openbox.shift.repository;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface ShiftArrangementRepository extends JpaRepository<ShiftArrangementDO, Integer> {
    List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndStartBetween(String username, ZonedDateTime left, ZonedDateTime right);
    @Query("select arrangement from ShiftArrangementDO arrangement where trim(arrangement.username) = :username and arrangement.start between :left and :right")
    List<ShiftArrangementDO> getShiftArrangementDOByTrimmedUsernameAndStartBetween(
            @Param("username") String username,
            @Param("left") ZonedDateTime left,
            @Param("right") ZonedDateTime right
    );
    List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndStatusAndStartBetween(String username, String status, ZonedDateTime left, ZonedDateTime right);
    List<ShiftArrangementDO> getShiftArrangementDOByStartBetween(ZonedDateTime start, ZonedDateTime end);
    List<ShiftArrangementDO> getShiftArrangementDOByGroupAndStartBetween(String groupName, ZonedDateTime start, ZonedDateTime end);
    List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndGroupAndStartBetween(String username, String groupName, ZonedDateTime start, ZonedDateTime end);
}
