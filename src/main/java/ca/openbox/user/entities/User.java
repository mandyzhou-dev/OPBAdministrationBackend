package ca.openbox.user.entities;

import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.dto.RegisterDTO;
import ca.openbox.user.repository.UserRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.io.File;
import java.time.LocalDate;
import java.util.Date;

@Data
@Configurable
public class User {
//Domain Entity
    private String username;
    private String verificationCode;
    private String name;
    private String password;
    private LocalDate birthdate;
    private String roles;
    private String legalname;
    private String sinno;
    private String address;
    private String phoneNumber;
    private String email;
    private String personalDocumentsPath;
    private File personalDocuments;
    private Integer active;
    //need to input the group name for each user in the database by hand
    private String groupName;
    private LocalDate bigDay;

    public UserDO getDO(){
        UserDO userDO = new UserDO();
        userDO.setUsername(username);
        userDO.setName(name);
        userDO.setPassword(password);
        userDO.setBirthdate(birthdate);
        userDO.setRoles(roles);
        userDO.setLegalname(legalname);
        userDO.setSinno(sinno);
        userDO.setAddress(address);
        userDO.setPhoneNumber(phoneNumber);
        userDO.setEmail(email);
        userDO.setActive(active);
        //userDO.setPersonalDocumentsPath(personalDocumentsPath);
        //wait for list
        userDO.setGroupName(groupName);
        userDO.setBigDay(bigDay);
        return userDO;
    }
    static public User fromDO(UserDO userDO){
        User user = new User();
        user.username = userDO.getUsername();
        user.password = userDO.getPassword();
        user.birthdate = userDO.getBirthdate();
        user.roles = userDO.getRoles();
        user.name = userDO.getName();
        user.legalname = userDO.getLegalname();
        user.sinno = userDO.getSinno();
        user.address = userDO.getAddress();
        user.phoneNumber = userDO.getPhoneNumber();
        user.email = userDO.getEmail();
        user.active = userDO.getActive();
        //user.personalDocumentsPath = userDO.getPersonalDocumentsPath();
        user.groupName = userDO.getGroupName();
        user.bigDay = userDO.getBigDay();
        return user;
    }


    @Autowired
    UserRepository userRepository;

    public User register(){
        UserDO userDO = userRepository.save(this.getDO());
        return this;
    }
}
