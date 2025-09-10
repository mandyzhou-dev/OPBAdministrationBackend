package ca.openbox.shift.service;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.statistics.service.WorkTimeStatisticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShiftArrangementService {
    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;

    public ShiftArrangement addArrangement(ShiftArrangement shiftArrangement){
        if(shiftArrangementRepository
                .getShiftArrangementDOByUsernameAndStartBetween(
                        shiftArrangement.getUsername(),
                        shiftArrangement.getStart(),shiftArrangement.getEnd()).size()==0) {
            ShiftArrangementDO insertedShiftArrangementDO = shiftArrangementRepository.save(shiftArrangement.getDO());
            return ShiftArrangement.fromDO(insertedShiftArrangementDO);
        }
        else{
            throw new DuplicateKeyException(String.format("There has been a shift exist for %s", shiftArrangement.getUsername()));
        }
    }
    public void deleteArrangement(ShiftArrangement shiftArrangement){
        shiftArrangementRepository.delete(shiftArrangement.getDO());
    }
    public ShiftArrangement modifyArrangement(ShiftArrangement shiftArrangement){
        ShiftArrangementDO modifiedShiftArrangementDO=shiftArrangementRepository.save(shiftArrangement.getDO());
        return ShiftArrangement.fromDO(modifiedShiftArrangementDO);
    }

    public List<ShiftArrangement> getByGroupAndDate(String groupName, ZonedDateTime date){
        ZonedDateTime start = date.withHour(0).withMinute(0).withSecond(0);
        ZonedDateTime end = date.withHour(23).withMinute(59).withSecond(59);
        List<ShiftArrangementDO> shiftArrangementDOList = shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween(groupName, start, end);
        return shiftArrangementDOList.stream().map(o -> ShiftArrangement.fromDO(o)).collect(Collectors.toList());
    }
    public List<ShiftArrangement> getByUserAndGroupAndDate(String username, String groupName, ZonedDateTime date){
        ZonedDateTime start = date.withHour(0).withMinute(0).withSecond(0);
        ZonedDateTime end = date.withHour(23).withMinute(59).withSecond(59);
        List<ShiftArrangementDO> shiftArrangementDOList = shiftArrangementRepository.getShiftArrangementDOByUsernameAndGroupAndStartBetween(username, groupName, start, end);
        return shiftArrangementDOList.stream().map(o -> ShiftArrangement.fromDO(o)).collect(Collectors.toList());
    }

}
