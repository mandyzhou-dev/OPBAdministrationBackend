package ca.openbox.user.service;

import ca.openbox.infrastructure.security.Cryptor;
import ca.openbox.infrastructure.variables.service.ApplicationVariableService;
import ca.openbox.user.dataobject.EmailVerificationDO;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.entities.User;
import ca.openbox.user.repository.EmailVerificationRepository;
import ca.openbox.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    UserRepository userRepository;
    @Autowired
    EmailVerificationRepository emailVerificationRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Cryptor cryptor;
    @Autowired
    ApplicationVariableService applicationVariableService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDO userDO = userRepository.getUserDOByUsernameAndActiveIsTrue(username);
        System.out.println(userDO.getRoles());
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(userDO.getUsername()).password(userDO.getPassword()).roles(userDO.getRoles().split("|")).build();
        return userDetails;
    }
    public User register(User user) throws Exception{
        UserDO userDO = user.getDO();
        userDO.setPassword(passwordEncoder.encode(userDO.getPassword()));
        userDO.setSinno(cryptor.encrypt(userDO.getSinno()));
        userRepository.save(userDO);
        return User.fromDO(userDO);
    }
    public void updateSIN(String username,String sinno) throws Exception{
        User user = getUserByUsername(username);
        user.setSinno(cryptor.encrypt(sinno));
        save(user);

    }
    public void setPassword(String username, String password){
        User user = getUserByUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        save(user);
    }
    public String getSinnoByUsername(String username) throws Exception {
        User user = getUserByUsername(username);
        String sinno = cryptor.decrypt(user.getSinno());
        return sinno;
    }
    public void save(User user){
        UserDO userDO = user.getDO();
        userRepository.save(userDO);
    }
    public User getUserByUsername(String username){
        UserDO userDO = userRepository.getUserDOByUsernameAndActiveIsTrue(username);
        if (userDO==null) return null;
        User user = User.fromDO(userDO);
        return user;
    }

    public String generateCode() {
        // 生成简单的6位数字验证码
        return String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
    }
    public void saveCode(String email, String code){
        EmailVerificationDO emailVerificationDO = new EmailVerificationDO();
        emailVerificationDO.setEmail(email);
        emailVerificationDO.setVerificationCode(code);
        emailVerificationRepository.save(emailVerificationDO);
    }
    public boolean verifyCode(String email,String code){
        EmailVerificationDO emailVerificationDO = emailVerificationRepository.getEmailVerificationDOByEmail(email);
        UserDO userDO = userRepository.getUserDOByEmail(email);
        if(userDO!=null) return false;
        return emailVerificationDO.getVerificationCode().equals(code);
    }
    public boolean isInProbation(String username) {
        User user = getUserByUsername(username);
        if(user==null) return true;
        if(user.getBigDay()==null) return true;
        LocalDate probationEnd
                = user.getBigDay().plusMonths(Integer.parseInt(applicationVariableService.getVariableValue("PROBATION_MONTHS")));
        return LocalDate.now().isBefore(probationEnd);
    }
    public void deactivate(String username) {
        User user = getUserByUsername(username);
        if(user.getActive() != null && user.getActive() == 0){//Already deactivated
            return;
        }
        user.setActive(0);
        save(user);
    }
}
