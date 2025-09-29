package ca.openbox.employment.controller;

import ca.openbox.employment.dto.TerminateDTO;
import ca.openbox.employment.entities.Employment;
import ca.openbox.employment.service.EmploymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("employment")
@RestController
public class EmploymentController {

    @Autowired
    EmploymentService employmentService;
    @CrossOrigin(origins = "http://localhost:8081")
    @PostMapping("/{username}/terminate")
    public void terminate(@PathVariable String username, @RequestBody TerminateDTO terminateDTO) {
        employmentService.terminate(
                username,terminateDTO.getLastDay(),terminateDTO.getTerminationReason()
        );
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{username}/employment")
    public Employment getEmployment(@PathVariable String username) {
        return employmentService.getEmploymentByUsername(username);
    }
}
