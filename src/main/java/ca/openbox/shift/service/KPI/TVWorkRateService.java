package ca.openbox.shift.service.KPI;

import ca.openbox.infrastructure.variables.service.ApplicationVariableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TVWorkRateService implements WorkRateService {
    @Autowired
    ApplicationVariableService applicationVariableService;

    @Override
    public double getRate() {
        return Double.parseDouble(applicationVariableService.getVariableValue("TV_daily_work_rate"));

    }

    @Override
    public void setRate(double rate) {
        applicationVariableService.setVariableValue("TV_daily_work_rate",String.valueOf(rate));
    }
}
