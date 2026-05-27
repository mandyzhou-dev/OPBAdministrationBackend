package ca.openbox.process.controller;

import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.dto.LeaveDateAvailabilityDateDTO;
import ca.openbox.process.service.LeaveApplicationService;
import ca.openbox.user.configuration.SecurityConfiguration;
import ca.openbox.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveApplicationController.class)
@Import(SecurityConfiguration.class)
class LeaveApplicationControllerAvailabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveApplicationService leaveApplicationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void leaveDateAvailabilityEndpointReturnsAvailabilityContract() throws Exception {
        when(leaveApplicationService.getLeaveDateAvailability(
                eq("employee1"),
                eq(LocalDate.parse("2026-05-27")),
                eq(LocalDate.parse("2026-05-28"))
        )).thenReturn(new LeaveDateAvailabilityDTO(
                "employee1",
                "2026-05-27",
                "2026-05-28",
                "America/Vancouver",
                List.of(
                        new LeaveDateAvailabilityDateDTO("2026-05-27", true, List.of(123)),
                        new LeaveDateAvailabilityDateDTO("2026-05-28", false, List.of())
                )
        ));

        mockMvc.perform(get("/process/application/leave-date-availability")
                        .header("Origin", "http://localhost:8081")
                        .param("applicant", "employee1")
                        .param("from", "2026-05-27")
                        .param("to", "2026-05-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicant").value("employee1"))
                .andExpect(jsonPath("$.from").value("2026-05-27"))
                .andExpect(jsonPath("$.to").value("2026-05-28"))
                .andExpect(jsonPath("$.businessZone").value("America/Vancouver"))
                .andExpect(jsonPath("$.dates[0].date").value("2026-05-27"))
                .andExpect(jsonPath("$.dates[0].scheduled").value(true))
                .andExpect(jsonPath("$.dates[0].shiftIds[0]").value(123))
                .andExpect(jsonPath("$.dates[1].scheduled").value(false))
                .andExpect(jsonPath("$.dates[1].shiftIds").isArray());
    }
}
