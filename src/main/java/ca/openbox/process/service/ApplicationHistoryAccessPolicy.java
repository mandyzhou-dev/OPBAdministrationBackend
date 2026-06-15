package ca.openbox.process.service;

import ca.openbox.process.entities.HistoryVisibility;
import ca.openbox.process.entities.HistoryVisibilityScope;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Component
public class ApplicationHistoryAccessPolicy {

    @Autowired
    UserRepository userRepository;

    public HistoryVisibility resolveVisibility(String operatorUsername) {
        if (operatorUsername == null || operatorUsername.isBlank()) {
            throw forbidden();
        }

        UserDO operator = userRepository.getUserDOByUsernameAndActiveIsTrue(operatorUsername);
        if (operator == null) {
            throw forbidden();
        }

        if (hasRole(operator.getRoles(), "Manager") || isManagerGroup(operator.getGroupName())) {
            return new HistoryVisibility(true, HistoryVisibilityScope.ALL_EMPLOYEES, operator.getGroupName());
        }

        throw forbidden();
    }

    private boolean hasRole(String roles, String expectedRole) {
        if (roles == null || roles.isBlank()) {
            return false;
        }
        return Arrays.stream(roles.split("\\|"))
                .map(String::trim)
                .anyMatch(role -> role.equalsIgnoreCase(expectedRole));
    }

    private boolean isManagerGroup(String groupName) {
        return groupName != null && groupName.trim().equalsIgnoreCase("manager");
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers can view application history.");
    }
}
