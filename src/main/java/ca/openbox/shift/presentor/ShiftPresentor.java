package ca.openbox.shift.presentor;

import ca.openbox.shift.presentation.ShiftPresentation;
import ca.openbox.shift.repository.ShiftPresentationRepository;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.entities.User;
import ca.openbox.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/presentor/shift/")
public class ShiftPresentor {
    @Autowired
    ShiftPresentationRepository shiftPresentationRepository;

    @Autowired
    UserRepository userRepository;

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/getShiftByStartDateScope")
    public Collection<ShiftPresentation> getShiftByStartDateScope(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
                                                                  @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end){
        return shiftPresentationRepository.getByTimeScope(start, end);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{username}/getMyShiftByStartDateScope")
    public Collection<ShiftPresentation> getMyShiftByStartDateScope(@PathVariable String username,
                                                                    @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
                                                                    @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end){
        return shiftPresentationRepository.getSchedulePresentationByUsernameAndStartdate(username,start,end);
    }

    /**
     * Retrieves visible shifts based on user roles and data isolation rules:
     * - Managers: Can view all shifts across the organization within the time scope.
     * - Employees: Can view shifts belonging to their Home Group OR shifts assigned to themselves (Support/Cross-store).
     */
    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{username}/findVisibleShifts")
    public Collection<ShiftPresentation> findVisibleShifts(@PathVariable String username,
                                                                 @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
                                                                 @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end
    ){
        UserDO userDO = userRepository.getUserDOByUsernameAndActiveIsTrue(username);
        if (userDO == null) return Collections.emptyList();
        String groupName = userDO.getGroupName();//user's homeGroup
        if("manager".equals(groupName)){//avoid the exp: grouname==null
            return shiftPresentationRepository.getByTimeScope(start, end);
        }
        else{
            return shiftPresentationRepository.getByGroupOrUsernameBetween(groupName,username, start,end);
        }
    }
}
