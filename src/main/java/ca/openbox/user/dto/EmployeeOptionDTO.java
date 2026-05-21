package ca.openbox.user.dto;

import ca.openbox.user.presentation.UserPresentation;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmployeeOptionDTO {
    private String username;
    private String name;
    private String roles;
    private String groupName;
    private Integer active;

    public static EmployeeOptionDTO fromPresentation(UserPresentation user) {
        return new EmployeeOptionDTO(
                user.getUsername(),
                user.getName(),
                user.getRoles(),
                user.getGroupName(),
                user.getActive()
        );
    }
}
