package ca.openbox.statistics.service;

import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.entities.ShiftStatus;
import ca.openbox.statistics.presentation.WorkTimeStatistic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class WorkLoadCalculator {

    public double calculateTotalWorkHour(List<ShiftArrangement> shiftArrangementList){
        // SUM UP THE WORK TIME STATISTICS AND GIVE A TOTAL WORK TIME
        double sum=0;
        for(int i =0;i<shiftArrangementList.size();++i){
            if (ShiftStatus.isNonWorked(shiftArrangementList.get(i).getStatus())) {
                continue;
            }
            sum+=shiftArrangementList.get(i).workMinutes();
        }

        return sum/60.0;
    }
}
