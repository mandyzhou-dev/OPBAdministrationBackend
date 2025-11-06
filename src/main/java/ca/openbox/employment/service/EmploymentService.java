package ca.openbox.employment.service;

import ca.openbox.employment.entities.Employment;
import ca.openbox.employment.repository.EmploymentRepository;
import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.resignation.service.ResignationApplicationService;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.entities.User;
import ca.openbox.user.repository.UserRepository;
import ca.openbox.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class EmploymentService {
    @Autowired
    UserService userService;

    @Autowired
    EmploymentRepository employmentRepository;
    @Autowired
    private ResignationApplicationService resignationApplicationService;

    public void terminate(String username, LocalDate lastDay, String terminationReason) {
        //0 initialize the employment record
        Employment employment = new Employment();
        //1.1 get User by username
        User user = userService.getUserByUsername(username);
        if(user == null) {throw new IllegalStateException("Employment already terminated");}
        //1.2 get Resignation Application by username
        ResignationApplication resignationApplication = resignationApplicationService.getResignationApplicationByApplicant(username);
        if(resignationApplication != null) {
            //NoticeDate (Need to retreive from resignation application)
            employment.setNoticeDate(resignationApplication.getSubmittedAt().
                    withZoneSameInstant(ZoneId.of("America/Vancouver")).toLocalDate());
        }

        //2. Add a new employment record
        // 2.1 set the username,lastDay, Reason,
        employment.setUsername(username);
        employment.setLastDay(lastDay);
        employment.setTerminationReason(terminationReason);
        //2.2. get the bigday, occ,salary,legalName from User
        employment.setBigDay(user.getBigDay());
        employment.setRoles(user.getRoles());
        employment.setLegalName(user.getLegalname());
        //2.3. Save the employment record
        employmentRepository.save(employment);
        //3. Deactivate the account
        userService.deactivate(username);
    }

    public Employment getEmploymentByUsername(String username) {
        return employmentRepository.getEmploymentByUsername(username);
    }
}
