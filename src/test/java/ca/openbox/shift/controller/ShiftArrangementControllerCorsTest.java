package ca.openbox.shift.controller;

import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.shift.service.ShiftArrangementService;
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

import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShiftArrangementController.class)
@Import(SecurityConfiguration.class)
class ShiftArrangementControllerCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShiftArrangementService shiftArrangementService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ShiftArrangementRepository shiftArrangementRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void preflightAllowsPatchStatusEndpoint() throws Exception {
        mockMvc.perform(options("/shift/shiftarrangement/2811/status")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")));
    }

    @Test
    void patchStatusEndpointIsNotBlockedBySecurity() throws Exception {
        ShiftArrangement updated = new ShiftArrangement();
        updated.setId(2811);
        updated.setUsername("employee");
        updated.setStatus("no_show");
        updated.setStart(ZonedDateTime.parse("2026-05-13T16:30:00Z"));
        updated.setEnd(ZonedDateTime.parse("2026-05-14T01:00:00Z"));
        updated.setGroupName("surrey");

        when(shiftArrangementService.updateStatus(eq(2811), eq("no_show"), eq("manager")))
                .thenReturn(updated);

        mockMvc.perform(patch("/shift/shiftarrangement/2811/status")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"no_show\",\"operatorUsername\":\"manager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("no_show"));
    }
}
