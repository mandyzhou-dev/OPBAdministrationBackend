package ca.openbox.infrastructure.email.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebhookEmailServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildEmailJsonEscapesControlCharactersAndQuotes() throws Exception {
        String content = "Line one\nLine two\t\"quoted\" path C:\\proof";

        String json = WebhookEmailService.buildEmailJson(
                "hr@example.com",
                "Sick Proof \"Submitted\"",
                content,
                "tok\\en"
        );

        JsonNode payload = objectMapper.readTree(json);
        assertEquals("hr@example.com", payload.get("recipient").asText());
        assertEquals("Sick Proof \"Submitted\"", payload.get("title").asText());
        assertEquals(content, payload.get("content").asText());
        assertEquals("tok\\en", payload.get("token").asText());
        assertFalse(json.contains("Line one\nLine two"));
    }
}
