package ca.openbox.shift.controller.KPI;

import ca.openbox.shift.application.KPIApplication;
import ca.openbox.shift.dto.BonusRateDTO;
import ca.openbox.shift.dto.WorkRateDTO;
import ca.openbox.shift.entities.KPI;
import ca.openbox.shift.service.KPI.BonusRateService;
import ca.openbox.shift.service.KPI.WorkRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/shift/kpi")

public class KPIController {
    @Autowired
    KPIApplication kpiApplication;
    @Autowired
    WorkRateService workRateService;
    @Autowired
    BonusRateService bonusRateService;

    @GetMapping("/groupName")
    public KPI getKPIByDateAndGroup(@RequestParam String groupName,@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd")Date date){
        return kpiApplication.ofDate(groupName, date);
    }
    @GetMapping("/user")
    public KPI getUserKPI(@RequestParam String username,
                          @RequestParam String groupName,
                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        return kpiApplication.ofDate(username, groupName, date);
    }
    @GetMapping("/groupName/biweek")
    public KPI getBiweekKPIByGroup(@RequestParam String groupName){
        return kpiApplication.ofBiweek(groupName);
    }
    @GetMapping("/user/biweek")
    public KPI getUserBiweekKPI(@RequestParam String username,
                                @RequestParam String groupName) {
        return kpiApplication.ofBiweek(username, groupName);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/target-rate")
    public double getKPIRate() {
        return workRateService.getRate();
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/target-rate")
    public void updateKPIRate(@RequestBody WorkRateDTO targetRate) {
        workRateService.setRate(targetRate.getTargetRate());
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/bonus-rate")
    public double getBonusRate() {
        return bonusRateService.getRate();
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/bonus-rate")
    public void updateBonusRate(@RequestBody BonusRateDTO bonusRateDTO) {
        bonusRateService.setRate(bonusRateDTO.getBonusRate());
    }

}
