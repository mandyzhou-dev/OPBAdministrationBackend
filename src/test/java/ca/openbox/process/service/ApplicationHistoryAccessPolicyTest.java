package ca.openbox.process.service;

import ca.openbox.process.entities.HistoryVisibilityScope;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationHistoryAccessPolicyTest {

    private UserRepository userRepository;
    private ApplicationHistoryAccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accessPolicy = new ApplicationHistoryAccessPolicy();
        accessPolicy.userRepository = userRepository;
    }

    @Test
    void managerRoleCanViewAllEmployeesHistory() {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(user("tester|Manager"));

        assertEquals(HistoryVisibilityScope.ALL_EMPLOYEES,
                accessPolicy.resolveVisibility("manager").getScope());
    }

    @Test
    void operatorUsernameLookupPreservesTrailingSpaces() {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("Harsimranjit Kaur ")).thenReturn(user("tester|Manager"));

        assertEquals(HistoryVisibilityScope.ALL_EMPLOYEES,
                accessPolicy.resolveVisibility("Harsimranjit Kaur ").getScope());
    }

    @Test
    void managerGroupNameRemainsCompatibleWithExistingPermissionPattern() {
        UserDO operator = user("tester");
        operator.setGroupName("manager");
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("manager")).thenReturn(operator);

        assertEquals(HistoryVisibilityScope.ALL_EMPLOYEES,
                accessPolicy.resolveVisibility("manager").getScope());
    }

    @Test
    void nonManagerIsForbiddenIncludingTeamLeaderForCurrentRelease() {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("lead")).thenReturn(user("team_leader"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> accessPolicy.resolveVisibility("lead"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void broadSubstringRoleDoesNotGrantManagerAccess() {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("employee")).thenReturn(user("assistant_manager"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> accessPolicy.resolveVisibility("employee"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void missingOperatorIsForbidden() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> accessPolicy.resolveVisibility(" "));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private UserDO user(String roles) {
        UserDO user = new UserDO();
        user.setUsername("operator");
        user.setRoles(roles);
        user.setActive(1);
        return user;
    }
}
