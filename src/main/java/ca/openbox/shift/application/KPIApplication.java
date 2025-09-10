package ca.openbox.shift.application;

import ca.openbox.infrastructure.variables.repository.ApplicationVariablesRepository;
import ca.openbox.shift.entities.KPI;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.entities.Sprint;
import ca.openbox.shift.service.KPI.KPICalculator;
import ca.openbox.shift.service.ShiftArrangementService;
import ca.openbox.shift.service.SprintService;
import ca.openbox.statistics.service.WorkLoadCalculator;
import ca.openbox.statistics.service.WorkTimeStatisticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;



@Component
public class KPIApplication {

    @Autowired
    private ShiftArrangementService shiftArrangementService;
    @Autowired
    private WorkTimeStatisticService workTimeStatisticService;
    @Autowired
    private WorkLoadCalculator workLoadCalculator;
    @Autowired
    private KPICalculator kpiCalculator;
    @Autowired
    private ApplicationVariablesRepository applicationVariablesRepository;
    @Autowired
    SprintService sprintService;


    public KPI ofDate(String group, Date date) {
        ZonedDateTime zonedDateTime = date.toInstant().atZone(ZoneId.systemDefault());
        List<ShiftArrangement> dayShiftList = shiftArrangementService.getByGroupAndDate(group, zonedDateTime);
        Double dayTotalWorkTime = workLoadCalculator.calculateTotalWorkHour(dayShiftList);
        // System.out.println(dayTotalWorkTime);
        return kpiCalculator.calculate(dayTotalWorkTime);
    }

    // Overload: specify both user and group
    public KPI ofDate(String username, String group, Date date) {
        //员工隶属组与KPI强绑定
        ZonedDateTime zonedDateTime = date.toInstant().atZone(ZoneId.systemDefault());
        List<ShiftArrangement> dayShiftList = shiftArrangementService.getByUserAndGroupAndDate(username, group, zonedDateTime);
        Double dayWorkTime = workLoadCalculator.calculateTotalWorkHour(dayShiftList);
        return kpiCalculator.calculate(dayWorkTime);
    }

    public KPI ofBiweek(String group) {

        Sprint sprint = sprintService.getCurrentSprint();

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                sprint.getStartTime().toLocalDate(),
                sprint.getEndTime().toLocalDate()
        ) + 1;

        Double totalWorkTime = 0.0;
        for (int i = 0; i < daysBetween; i++) {
            ZonedDateTime currentDate = sprint.getStartTime().plusDays(i);
            List<ShiftArrangement> dayShiftList = shiftArrangementService.getByGroupAndDate(group, currentDate);
            Double dayWorkTime = workLoadCalculator.calculateTotalWorkHour(dayShiftList);
            totalWorkTime += dayWorkTime;
        }

        KPI biweekKPI = kpiCalculator.calculate(totalWorkTime);
        biweekKPI.setStartDateTime(sprint.getStartTime());
        biweekKPI.setEndDateTime(sprint.getEndTime());
        return biweekKPI;
    }

    // Overload: specify both user and group
    public KPI ofBiweek(String username, String group) {
        Sprint sprint = sprintService.getCurrentSprint();
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                sprint.getStartTime().toLocalDate(),
                sprint.getEndTime().toLocalDate()
        ) + 1;
        Double totalWorkTime = 0.0;
        for (int i = 0; i < daysBetween; i++) {
            ZonedDateTime currentDate = sprint.getStartTime().plusDays(i);
            List<ShiftArrangement> dayShiftList = shiftArrangementService.getByUserAndGroupAndDate(username, group, currentDate);
            Double dayWorkTime = workLoadCalculator.calculateTotalWorkHour(dayShiftList);
            totalWorkTime += dayWorkTime;
        }

        KPI biweekKPI = kpiCalculator.calculate(totalWorkTime);
        biweekKPI.setStartDateTime(sprint.getStartTime());
        biweekKPI.setEndDateTime(sprint.getEndTime());
        return biweekKPI;
    }
}
