package ca.openbox.user.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationTest {

    @Test
    void corsAllowsPatchPreflightForShiftStatusEndpoint() {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/shift/shiftarrangement/2811/status");
        request.addHeader("Origin", "http://localhost:8081");
        request.addHeader("Access-Control-Request-Method", "PATCH");

        CorsConfiguration corsConfiguration = securityConfiguration.corsConfigurationSource().getCorsConfiguration(request);

        assertNotNull(corsConfiguration);
        assertTrue(corsConfiguration.getAllowedMethods().contains("PATCH"));
    }
}
