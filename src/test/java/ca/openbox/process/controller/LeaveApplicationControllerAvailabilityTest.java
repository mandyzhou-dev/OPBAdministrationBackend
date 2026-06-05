package ca.openbox.process.controller;

import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.dto.LeaveDateAvailabilityDateDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.service.LeaveApplicationService;
import ca.openbox.user.configuration.SecurityConfiguration;
import ca.openbox.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveApplicationController.class)
@Import(SecurityConfiguration.class)
class LeaveApplicationControllerAvailabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void leaveApplicationEndpointAcceptsTrimmedHistoricalApplicantForSickLeaveSubmit() throws Exception {
        LeaveApplication saved = new LeaveApplication();
        saved.setId(831);
        saved.setApplicant("Harsimranjit Kaur");
        saved.setLeaveType("SICK");
        saved.setStart(ZonedDateTime.parse("2026-08-31T16:00:00Z"));
        saved.setEnd(ZonedDateTime.parse("2026-09-01T00:00:00Z"));
        saved.setStatus("pending");

        when(leaveApplicationService.addLeaveApplication(argThat(application ->
                application != null
                        && "Harsimranjit Kaur".equals(application.getApplicant())
                        && "SICK".equals(application.getLeaveType())
        ))).thenReturn(saved);

        mockMvc.perform(put("/process/application/leave-application")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PutLeaveApplicationRequest(
                                "Harsimranjit Kaur",
                                "SICK",
                                "2026-08-31T16:00:00Z",
                                "2026-09-01T00:00:00Z",
                                "sick"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(831))
                .andExpect(jsonPath("$.applicant").value("Harsimranjit Kaur"))
                .andExpect(jsonPath("$.leaveType").value("SICK"));
    }

    private record PutLeaveApplicationRequest(String applicant,
                                              String leaveType,
                                              String start,
                                              String end,
                                              String reason) {
    }
}
