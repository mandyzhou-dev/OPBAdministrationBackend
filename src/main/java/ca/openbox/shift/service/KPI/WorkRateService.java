package ca.openbox.shift.service.KPI;

import org.springframework.stereotype.Service;

@Service
public interface WorkRateService {
    double getRate();

    void setRate(double rate);
}
