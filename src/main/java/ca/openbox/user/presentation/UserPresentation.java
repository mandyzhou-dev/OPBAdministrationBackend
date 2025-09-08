package ca.openbox.user.presentation;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;



@Data
@Entity
@Table(name = "opb_user")
public class UserPresentation {
    //CQRS using presentation
    //Query from db,not crossing entity
    @Id
    private String username;
    private Integer active;
    private String name;
    private String roles;
    private String groupName;

}
