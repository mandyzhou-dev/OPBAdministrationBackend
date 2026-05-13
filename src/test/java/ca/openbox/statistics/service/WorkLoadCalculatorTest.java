package ca.openbox.statistics.service;

import ca.openbox.shift.entities.ShiftArrangement;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkLoadCalculatorTest {

    @Test
    void calculateTotalWorkHourSkipsNonWorkedShiftStatuses() {
        WorkLoadCalculator calculator = new WorkLoadCalculator();

        double hours = calculator.calculateTotalWorkHour(List.of(
                shift("active"),
                shift("cancelled"),
                shift("no_show"),
                shift("paid_sick_leave"),
                shift("unpaid_sick_leave")
        ));

        assertEquals(7.5, hours);
    }

    private ShiftArrangement shift(String status) {
        ZonedDateTime start = ZonedDateTime.of(2026, 5, 13, 9, 30, 0, 0, ZoneId.of("America/Vancouver"));
        ShiftArrangement shift = new ShiftArrangement();
        shift.setUsername("employee");
        shift.setStatus(status);
        shift.setStart(start);
        shift.setEnd(start.plusHours(8));
        return shift;
    }
}
