package ca.openbox.resignation.controller;
import ca.openbox.resignation.dto.PostResignationApplicationDTO;
import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.resignation.service.ResignationApplicationService;
import ca.openbox.resignation.service.components.ResignationMessageQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/resignations")
public class ResignationApplicationController {
    @Autowired
    ResignationApplicationService resignationApplicationService;

    @CrossOrigin(origins = "http://localhost:8081")
    @PostMapping
    public ResignationApplication createResignation(@RequestBody PostResignationApplicationDTO postResignationApplicationDTO) throws Exception {
        ResignationApplication resignationApplication = new ResignationApplication();
        resignationApplication.setApplicant(postResignationApplicationDTO.getApplicant());
        resignationApplication.setReason(postResignationApplicationDTO.getReason());
        resignationApplication.setLastWorkingDay(postResignationApplicationDTO.getLastWorkingDate());
        resignationApplication.setSubmittedAt(ZonedDateTime.now());
        ResignationApplication savedApplication = resignationApplicationService.addResignationApplication(resignationApplication);
        ResignationMessageQueue.put(savedApplication);
        return savedApplication;
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping
    public List<ResignationApplication> getAllResignationApplications() throws Exception {
        return resignationApplicationService.getAllResignationApplications();
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/{id}")
    public void reviewApplication(@PathVariable Integer id) {
        resignationApplicationService.reviewApplication(id);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{applicant}")
    public ResignationApplication getResignationApplicationByApplicant(@PathVariable String applicant) {
        return resignationApplicationService.getResignationApplicationByApplicant(applicant);
    }

}
