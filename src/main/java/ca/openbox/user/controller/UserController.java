package ca.openbox.user.controller;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.infrastructure.jwt.JwtUtil;
import ca.openbox.infrastructure.security.Cryptor;
import ca.openbox.process.service.EmailService;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.dto.LoginDTO;
import ca.openbox.user.dto.RegisterDTO;
import ca.openbox.user.dto.UserDTO;
import ca.openbox.user.entities.User;
import ca.openbox.user.repository.UserRepository;
import ca.openbox.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RequestMapping("user")
@RestController
public class UserController {
    @Autowired
    UserService userService;
    @Autowired
    WebhookEmailService emailService;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    AuthenticationManager authenticationManager;
    @CrossOrigin(origins = "http://localhost:8081", allowCredentials="true", allowedHeaders = {"*"})
    @GetMapping("/check_validation")
    public boolean checkUserValidation(@RequestParam String username){
        User user = userService.getUserByUsername(username);
        if(user==null)return false;
        return true;
    }
    @CrossOrigin(origins = "http://localhost:8081", allowCredentials="true", allowedHeaders = {"*"})
    @PostMapping("/login")
    public Object login(@RequestBody LoginDTO loginDTO, HttpServletRequest request){
        System.out.println(request.getSession().getId());
        Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(loginDTO.getUsername(),
                loginDTO.getPassword());
        Authentication authenticationResponse = this.authenticationManager.authenticate(authenticationRequest);
        User user =userService.getUserByUsername(loginDTO.getUsername());
        UserDTO userDTO = new UserDTO();
        userDTO.setName(user.getName());
        userDTO.setUsername(user.getUsername());
        userDTO.setRoles(user.getRoles());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhoneNumber(user.getPhoneNumber());
        userDTO.setAddress(user.getAddress());
        userDTO.setBirthdate(user.getBirthdate());
        userDTO.setJSessionID(request.getSession(true).getId());
        userDTO.setActive(user.getActive());
        userDTO.setToken(jwtUtil.generateToken(user));
        return userDTO;
    }


    @Autowired
    Cryptor cryptor;
    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("/send_code")
    public void sendCode(@RequestParam String email) throws Exception{
        //Click the "send code"
        String code = userService.generateCode();
        emailService.sendEmail(email,"Verification Code for registration","Use this code to finish setting up your account:"+code);
        userService.saveCode(email,code);
    }
    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("/register")
    public Object register(@RequestBody RegisterDTO registerDTO,@RequestParam String code) throws Exception{
        //Click the "register now"
        System.out.println(registerDTO);
        System.out.println("code"+code);
        boolean isVerified = userService.verifyCode(registerDTO.getEmail(), code);
        if(isVerified){
            User user = new User();
            user.setUsername(registerDTO.getUsername());
            user.setName(registerDTO.getName());
            user.setPassword(registerDTO.getPassword());
            user.setBirthdate(registerDTO.getBirthdate());
            user.setRoles("tester");
            user.setLegalname(registerDTO.getLegalname());
            user.setSinno(registerDTO.getSinno());
            user.setAddress(registerDTO.getAddress());
            user.setPhoneNumber(registerDTO.getPhoneNumber());
            user.setEmail(registerDTO.getEmail());
            user.setActive(1);
            return userService.register(user);
        }
        else{
            throw new IllegalAccessException();
        }
    }
    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("{username}/updatesinno")
    public void updateSIN(@PathVariable String username, @RequestBody String sinno) throws Exception {
        userService.updateSIN(username,sinno);
    }
    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("/{username}/password")
    public void resetPassword(@PathVariable String username, @RequestBody String password){
        userService.setPassword(username,password);

    }
    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{username}/getSinno")
    public String getSinnoByUsername(@PathVariable String username) throws Exception {
        return userService.getSinnoByUsername(username);
    }
    @CrossOrigin(origins = "http://localhost:8081")
    @PostMapping("/{username}/modifyprofile")
    public void modifyProfile(@PathVariable String username ,@RequestBody UserDTO userDTO){
        User user = userService.getUserByUsername(username);
        //donnot modify username!
        //Do not empty any attribute
        user.setEmail(userDTO.getEmail());
        user.setBirthdate(userDTO.getBirthdate());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        user.setAddress(userDTO.getAddress());
        userService.save(user);
    }
    @RequestMapping("/csrf")
    public CsrfToken csrf(CsrfToken token){
        return token;
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{username}/probation")
    public boolean isInProbation(@PathVariable String username) {
        return userService.isInProbation(username);
    }
}
