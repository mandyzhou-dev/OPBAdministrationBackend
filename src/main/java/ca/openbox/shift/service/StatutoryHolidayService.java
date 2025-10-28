package ca.openbox.shift.service;

import ca.openbox.shift.dataobject.StatutoryHolidayDO;
import ca.openbox.shift.repository.StatutoryHolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatutoryHolidayService {
    @Autowired
    private StatutoryHolidayRepository statutoryHolidayRepository;
    public List<LocalDate> getAllHolidayDates() {
        return statutoryHolidayRepository.findAllHolidayDates();
    }
    public List<StatutoryHolidayDO>  findAllHolidays() {
        return statutoryHolidayRepository.findAll();
    }
}
