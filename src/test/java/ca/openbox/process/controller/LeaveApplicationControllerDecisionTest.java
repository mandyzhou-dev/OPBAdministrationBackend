package ca.openbox.process.controller;

import ca.openbox.process.service.LeaveApplicationService;
import ca.openbox.user.configuration.SecurityConfiguration;
import ca.openbox.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveApplicationController.class)
@Import(SecurityConfiguration.class)
class LeaveApplicationControllerDecisionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveApplicationService leaveApplicationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void permitAcceptsJsonReviewComment() throws Exception {
        mockMvc.perform(post("/process/application/12/permit")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewComment\":\"Approved with handoff\"}"))
                .andExpect(status().isOk());

        verify(leaveApplicationService).permitApplication(12, "Approved with handoff");
    }

    @Test
    void permitAcceptsNoBody() throws Exception {
        mockMvc.perform(post("/process/application/13/permit")
                        .header("Origin", "http://localhost:8081"))
                .andExpect(status().isOk());

        verify(leaveApplicationService).permitApplication(13, null);
    }

    @Test
    void rejectAcceptsJsonReviewComment() throws Exception {
        mockMvc.perform(post("/process/application/14/reject")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewComment\":\"Insufficient coverage\"}"))
                .andExpect(status().isOk());

        verify(leaveApplicationService).rejectApplication(14, "Insufficient coverage");
    }
}
