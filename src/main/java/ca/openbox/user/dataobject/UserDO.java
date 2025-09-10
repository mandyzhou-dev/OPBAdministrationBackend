package ca.openbox.user.dataobject;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;


@Data
@Entity
@Table(name="opb_user")
public class UserDO {
    @Id
    private String username;
    private String name;
    private String password;
    private LocalDate birthdate;
    private String roles;
    private String legalname;
    private String sinno;
    private String address;
    private String phoneNumber;
    private String email;
    //private String personalDocumentsPath;
    //cause of list
    private Integer active;
    //new
    private String groupName;
    private LocalDate bigDay;
}
