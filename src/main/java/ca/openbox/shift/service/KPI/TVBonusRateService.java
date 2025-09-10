package ca.openbox.shift.service.KPI;

import ca.openbox.infrastructure.variables.service.ApplicationVariableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TVBonusRateService implements BonusRateService {

    @Autowired
    ApplicationVariableService applicationVariableService;

    @Override
    public double getRate() {
        return Double.parseDouble(applicationVariableService.getVariableValue("bonus_rate"));

    }

    @Override
    public void setRate(double rate) {
        applicationVariableService.setVariableValue("bonus_rate",String.valueOf(rate));
    }
}
