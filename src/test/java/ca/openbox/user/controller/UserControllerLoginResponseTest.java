package ca.openbox.user.controller;

import ca.openbox.user.dto.LoginResponseDTO;
import ca.openbox.user.entities.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserControllerLoginResponseTest {
    @Test
    void toLoginResponseMapsOnlyMinimalFieldsWithoutChangingUsername() {
        UserController controller = new UserController();
        User user = new User();
        user.setUsername("Hermissan Kaur ");
        user.setName("Hermissan Kaur");
        user.setRoles("tester|Manager");
        user.setGroupName("surrey");
        user.setEmail("private@example.com");
        user.setPhoneNumber("6045550100");
        user.setAddress("123 Private Street");
        user.setBirthdate(LocalDate.of(1990, 1, 15));
        user.setLegalname("Legal Private Name");
        user.setSinno("encrypted-sin");
        user.setActive(1);

        LoginResponseDTO response = controller.toLoginResponse(user, "session-123");

        assertEquals("Hermissan Kaur ", response.getUsername());
        assertEquals("Hermissan Kaur", response.getName());
        assertEquals("tester|Manager", response.getRoles());
        assertEquals("surrey", response.getGroupName());
        assertEquals("session-123", response.getJsessionID());
        assertNull(response.getToken(), "token is added after JwtUtil generation in login()");
    }
}
