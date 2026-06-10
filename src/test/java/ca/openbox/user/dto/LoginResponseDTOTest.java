package ca.openbox.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LoginResponseDTOTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyMinimalLoginFields() throws Exception {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setUsername("employee1");
        dto.setName("Employee One");
        dto.setRoles("tester|Manager");
        dto.setGroupName("surrey");
        dto.setJsessionID("session-123");
        dto.setToken("jwt-token");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertEquals("employee1", json.get("username").asText());
        assertEquals("Employee One", json.get("name").asText());
        assertEquals("tester|Manager", json.get("roles").asText());
        assertEquals("surrey", json.get("groupName").asText());
        assertEquals("session-123", json.get("jsessionID").asText());
        assertEquals("jwt-token", json.get("token").asText());

        assertFalse(json.has("email"));
        assertFalse(json.has("phoneNumber"));
        assertFalse(json.has("address"));
        assertFalse(json.has("birthdate"));
        assertFalse(json.has("legalname"));
        assertFalse(json.has("sinno"));
        assertFalse(json.has("password"));
        assertFalse(json.has("active"));
        assertFalse(json.has("bigDay"));
    }
}
