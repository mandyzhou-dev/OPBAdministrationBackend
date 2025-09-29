package ca.openbox.employment.service;

import ca.openbox.employment.entities.Employment;
import ca.openbox.employment.repository.EmploymentRepository;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.entities.User;
import ca.openbox.user.repository.UserRepository;
import ca.openbox.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class EmploymentService {
    @Autowired
    UserService userService;

    @Autowired
    EmploymentRepository employmentRepository;
    public void terminate(String username, LocalDate lastDay, String terminationReason) {
        //1. get User by username
        User user = userService.getUserByUsername(username);
        if(user == null) {throw new IllegalStateException("Employment already terminated");}
        //2. Add a new employment record
        // 2.1 New employment(),set the username,lastDay, Reason,
        Employment employment = new Employment();
        employment.setUsername(username);
        employment.setLastDay(lastDay);
        employment.setTerminationReason(terminationReason);
        //2.2. get the bigday, occ,salary,legalName from User
        employment.setBigDay(user.getBigDay());
        employment.setRoles(user.getRoles());
        employment.setLegalName(user.getLegalname());
        //2.3. TODO: NoticeDate (Need to retreive from resignation application)
        //2.4. Save the employment record
        employmentRepository.save(employment);
        //3. Deactivate the account
        userService.deactivate(username);
    }

    public Employment getEmploymentByUsername(String username) {
        return employmentRepository.getEmploymentByUsername(username);
    }
}
