package ca.openbox.shift.dto;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
@Data
public class StatutoryHolidayDTO {
    private Integer id;
    private LocalDate statutoryDate;
    private String holidayName;
}
