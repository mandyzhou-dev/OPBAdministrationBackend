package ca.openbox.statistics.presentor;

import ca.openbox.shift.presentation.ShiftPresentation;
import ca.openbox.shift.entities.ShiftStatus;
import ca.openbox.shift.presentor.ShiftPresentor;
import ca.openbox.shift.repository.ShiftPresentationRepository;
import ca.openbox.statistics.presentation.WorkTimeStatistic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
@RestController
@RequestMapping("/presentor/statistic/work-time-statistic")
public class WorkTimeStatisticsPresentor {
    @Autowired
    ShiftPresentationRepository shiftPresentationRepository;

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping
    public List<WorkTimeStatistic> getByTimeScope(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
                                                          @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        //Calculate the data from ShiftPresentations retrieved by ShiftPresentor
        List<WorkTimeStatistic> resultList = new ArrayList<>();
        HashMap<String, WorkTimeStatistic> resultMap = new HashMap<>();
        List<ShiftPresentation> shiftList = shiftPresentationRepository.getByTimeScope(start, end);

        ZoneId vancouverZone = ZoneId.of("America/Vancouver");

        for (int i = 0; i < shiftList.size(); ++i) {
            if (ShiftStatus.isNonWorked(shiftList.get(i).getStatus())) {
                continue;
            }

            WorkTimeStatistic workTimeStatistic = new WorkTimeStatistic();
            String username = shiftList.get(i).getUsername();

            workTimeStatistic.setUsername(username);
            workTimeStatistic.setUserRealName(shiftList.get(i).getUserRealName());

            ZonedDateTime shiftStart = shiftList.get(i).getStart();
            ZonedDateTime shiftEnd = shiftList.get(i).getEnd();

            ZonedDateTime shiftStartYVR = shiftStart.withZoneSameInstant(vancouverZone);
            ZonedDateTime shiftEndYVR = shiftEnd.withZoneSameInstant(vancouverZone);

            Duration duration = Duration.between(shiftStart, shiftEnd);

            ZonedDateTime lunchStart = shiftStartYVR
                    .toLocalDate()
                    .atTime(12, 0)
                    .atZone(vancouverZone);
            ZonedDateTime lunchEnd = lunchStart.plusMinutes(30);

            if(duration.toMinutes()>300){//5hours on work up minus lunch
                duration= duration.minusMinutes(30);
            }
            else if(shiftStartYVR.isBefore(lunchEnd)
                    && shiftEndYVR.isAfter(lunchStart)
                    && shiftList.get(i).getGroupName().equalsIgnoreCase("surrey")){//coversLunchBreak
                duration = duration.minusMinutes(30);
            }

            if(resultMap.containsKey(username)){
                duration = duration.plusMinutes(resultMap.get(username).getMinutes());
            }

            workTimeStatistic.setMinutes(duration.toMinutes());
            resultMap.put(username,workTimeStatistic);
        }
        for(WorkTimeStatistic workTimeStatistic:resultMap.values()){
            workTimeStatistic.setHours(workTimeStatistic.getMinutes()/60.0);
            resultList.add(workTimeStatistic);
        }
        return resultList;
    }
}
