package ca.openbox.process.controller;

import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.dto.PutLeaveApplicationDTO;
import ca.openbox.process.dto.ReviewDecisionDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.service.LeaveApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/process")
public class LeaveApplicationController {
    @Autowired
    LeaveApplicationService leaveApplicationService;
    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/application/leave-application")
    public LeaveApplication leaveApplication(@RequestBody PutLeaveApplicationDTO putLeaveApplicationDTO) throws Exception {
        LeaveApplication leaveApplication = new LeaveApplication();
        leaveApplication.setApplicant(putLeaveApplicationDTO.getApplicant());
        leaveApplication.setLeaveType(putLeaveApplicationDTO.getLeaveType());
        leaveApplication.setStart(putLeaveApplicationDTO.getStart());
        leaveApplication.setEnd(putLeaveApplicationDTO.getEnd());
        leaveApplication.setStatus("pending");
        leaveApplication.setSubmitTime(ZonedDateTime.now());
        leaveApplication.setCurrentHandler("raynold,agnes");
        leaveApplication.setReason(putLeaveApplicationDTO.getReason());
        LeaveApplication savedApplication = leaveApplicationService.addLeaveApplication(leaveApplication);
        return savedApplication;
    }

    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("/application/{applicationID}/permit")
    public void permit(@PathVariable Integer applicationID,
                       @RequestBody(required = false) ReviewDecisionDTO reviewDecisionDTO){
        leaveApplicationService.permitApplication(applicationID, reviewCommentFrom(reviewDecisionDTO));
    }

    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.POST})
    @PostMapping("/application/{applicationID}/reject")
    public void reject(@PathVariable Integer applicationID,@RequestBody ReviewDecisionDTO reviewDecisionDTO){
        leaveApplicationService.rejectApplication(applicationID, reviewCommentFrom(reviewDecisionDTO));
    }
    @CrossOrigin(origins = "http://localhost:8081",methods = {RequestMethod.DELETE})
    @DeleteMapping("/application/{applicationID}")
    public void delete(@PathVariable Integer applicationID){
        leaveApplicationService.deleteApplication(applicationID);
    }

    @CrossOrigin(origins ="http://localhost:8081",methods = {RequestMethod.GET})
    @GetMapping("/application")
    public List<LeaveApplication> getApplicationsByApplicant(@RequestParam(value = "handler",required = false) String handler,
                                                             @RequestParam(value = "applicant",required = false) String applicant){
        if(handler != null && !handler.equals("")){
            System.out.println(handler);
            return leaveApplicationService.getApplicationsByHandler(handler);
        }
        if(applicant !=null && !applicant.equals("")){
            return leaveApplicationService.getApplicationsByApplicant(applicant);
        }
        return leaveApplicationService.getAllApplications();
    }

    @CrossOrigin(origins ="http://localhost:8081",methods = {RequestMethod.GET})
    @GetMapping("/application/history")
    public PageResponseDTO<LeaveApplication> getApplicationHistory(@RequestParam(value = "operatorUsername") String operatorUsername,
                                                                   @RequestParam(value = "employeeUsername",required = false) String employeeUsername,
                                                                   @RequestParam(value = "page",required = false, defaultValue = "0") int page,
                                                                   @RequestParam(value = "size",required = false, defaultValue = "20") int size,
                                                                   @RequestParam(value = "sort",required = false, defaultValue = "submitTime,desc") String sort){
        return leaveApplicationService.getHistory(employeeUsername, page, size, sort, operatorUsername);
    }

    @CrossOrigin(origins ="http://localhost:8081",methods = {RequestMethod.GET})
    @GetMapping("/application/leave-date-availability")
    public LeaveDateAvailabilityDTO getLeaveDateAvailability(@RequestParam(value = "applicant") String applicant,
                                                             @RequestParam(value = "from") LocalDate from,
                                                             @RequestParam(value = "to") LocalDate to){
        return leaveApplicationService.getLeaveDateAvailability(applicant, from, to);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/application/{applicationID}/note")
    public void putNote(@PathVariable Integer applicationID, @RequestBody String note){
        leaveApplicationService.addNoteToApplication(applicationID,note);
    }

    @CrossOrigin(origins = "http://localhost:8081", methods = {RequestMethod.POST})
    @PostMapping("/application/{applicationID}/sick-proof")
    public LeaveApplication uploadSickProof(@PathVariable Integer applicationID,
                                            @RequestParam("proof") MultipartFile proof,
                                            @RequestParam("applicant") String applicant) {
        return leaveApplicationService.uploadSickProof(applicationID, applicant, proof);
    }

    private String reviewCommentFrom(ReviewDecisionDTO reviewDecisionDTO) {
        return reviewDecisionDTO == null ? null : reviewDecisionDTO.getReviewComment();
    }
}
