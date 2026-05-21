package ca.openbox.user.presentor;

import ca.openbox.user.dto.EmployeeOptionDTO;
import ca.openbox.user.presentation.UserPresentation;
import ca.openbox.user.repository.UserPresentationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPresentorEmployeeOptionsTest {

    private UserPresentationRepository userPresentationRepository;
    private UserPresentor userPresentor;

    @BeforeEach
    void setUp() {
        userPresentationRepository = mock(UserPresentationRepository.class);
        userPresentor = new UserPresentor();
        userPresentor.userPresentationRepository = userPresentationRepository;
    }

    @Test
    void employeeOptionsReturnsActiveEmployeesOnlyByDefault() {
        when(userPresentationRepository.findByRolesContainingAndActiveOrderByNameAsc("tester", 1))
                .thenReturn(List.of(user("jane", "Jane Doe", 1), user("amy", "Amy Lee", 1)));

        Collection<EmployeeOptionDTO> options = userPresentor.getEmployeeOptions(true);

        assertEquals(2, options.size());
        assertEquals(List.of("jane", "amy"), options.stream().map(EmployeeOptionDTO::getUsername).toList());
    }

    @Test
    void employeeOptionsCanIncludeInactiveEmployees() {
        when(userPresentationRepository.findByRolesContainingOrderByActiveDesc("tester"))
                .thenReturn(List.of(user("jane", "Jane Doe", 1), user("max", "Max Doe", 0)));

        Collection<EmployeeOptionDTO> options = userPresentor.getEmployeeOptions(false);

        assertEquals(List.of(1, 0), options.stream().map(EmployeeOptionDTO::getActive).toList());
    }

    private UserPresentation user(String username, String name, Integer active) {
        UserPresentation user = new UserPresentation();
        user.setUsername(username);
        user.setName(name);
        user.setRoles("tester");
        user.setGroupName("surrey");
        user.setActive(active);
        return user;
    }
}
