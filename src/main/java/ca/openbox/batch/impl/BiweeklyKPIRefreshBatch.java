package ca.openbox.batch.impl;

import ca.openbox.batch.BatchTask;
import ca.openbox.infrastructure.variables.service.ApplicationVariableService;
import ca.openbox.shift.application.KPIApplication;
import ca.openbox.shift.entities.KPI;
import ca.openbox.shift.entities.KPIRecord;
import ca.openbox.shift.service.KPI.KPIRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class BiweeklyKPIRefreshBatch implements BatchTask {
    @Autowired
    ApplicationVariableService applicationVariableService;
    @Autowired
    private KPIRecordService kpiRecordService;
    @Autowired
    KPIApplication kpiApplication;

    @Override
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void execute() throws Exception {
        //get the current expected biweek.
        KPI kpi = kpiApplication.ofBiweek("surrey");
        //second minute hour day-of-month month day-of-week
        // 1. Update KPI StartDate
        // 1.1 Get the start date in db
        LocalDate startDateDB = LocalDate.parse(applicationVariableService.getVariableValue("SprintBiweekStartDate"));

        // 1.2 Check is current date is 14 days later from db start date.
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(startDateDB, today);
        if(days % 14 ==0 && days >0){
            // 1.2.1 If it is, update the db else skip(RETURN)
            applicationVariableService.setVariableValue("SprintBiweekStartDate", today.toString());
        }
        else return;

        // TODO: 2. Record the KPI of last week
        //2.1 Calculate the biweek TV target from startDateDB
        //Calculeted in symbol kpi
        //2.2 update the db
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/dd");
        KPIRecord kpiRecord = new KPIRecord();

        String year = String.valueOf(startDateDB.getYear());
        String result = year+" "+
                startDateDB.format(fmt) + "-"
                        + startDateDB.plusDays(14).format(fmt);
        kpiRecord.setTitle(result);

        kpiRecord.setExpected(kpi.getTarget());
        kpiRecord.setActual(kpi.getTarget());
        kpiRecordService.createOrUpdateKpiRecord(kpiRecord);
    }
}
