package ca.openbox.shift.service.KPI;

import ca.openbox.shift.entities.KPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KPICalculator {

    @Autowired
    WorkRateService workRateService;

    @Autowired
    BonusRateService bonusRateService;

    private Double getTVWorkRate(){
        return workRateService.getRate();
    }
    private Double getTVBonusRate(){
        return bonusRateService.getRate();
    }

    public KPI calculate(Double workHour){
        //Calculate KPI
        KPI kpi = new KPI();
        kpi.setTarget((int) Math.ceil(workHour * getTVWorkRate()));
        kpi.setBonus((int) Math.ceil(workHour * getTVBonusRate()));
        return kpi;
    }
}
