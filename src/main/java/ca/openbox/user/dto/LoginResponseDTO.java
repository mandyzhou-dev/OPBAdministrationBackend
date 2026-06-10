package ca.openbox.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoginResponseDTO {
    private String username;
    private String name;
    private String roles;
    private String groupName;
    @JsonProperty("jsessionID")
    private String jsessionID;
    private String token;
}
