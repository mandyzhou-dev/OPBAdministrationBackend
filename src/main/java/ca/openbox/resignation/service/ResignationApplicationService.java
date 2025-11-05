package ca.openbox.resignation.service;

import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.resignation.entities.ResignationApplicationStatus;
import ca.openbox.resignation.repository.ResignationApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ResignationApplicationService {
    @Autowired
    ResignationApplicationRepository resignationApplicationRepository;
    public ResignationApplication addResignationApplication(ResignationApplication resignationApplication) {
        boolean hasActive = resignationApplicationRepository.existsByApplicantAndStatusIn(
                resignationApplication.getApplicant(),
                List.of(ResignationApplicationStatus.PENDING_REVIEW, ResignationApplicationStatus.REVIEWED)
        );

        if (hasActive) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You already have a pending resignation.To make any changes to your resignation request, please contact the system administrator."
            );
        }
        return resignationApplicationRepository.save(resignationApplication);
    }

    public void reviewApplication(Integer id) {
        ResignationApplication resignationApplication = resignationApplicationRepository.getResignationApplicationById(id);
        resignationApplication.setStatus(ResignationApplicationStatus.REVIEWED);
        resignationApplicationRepository.save(resignationApplication);
    }
    public List<ResignationApplication> getAllResignationApplications() {
        return resignationApplicationRepository.findAll();
    }

    public ResignationApplication getResignationApplicationByApplicant(String applicant) {
        return resignationApplicationRepository.getResignationApplicationByApplicant(applicant);
    }
}
