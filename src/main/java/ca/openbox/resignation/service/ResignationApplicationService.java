package ca.openbox.resignation.service;

import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.resignation.entities.ResignationApplicationStatus;
import ca.openbox.resignation.repository.ResignationApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResignationApplicationService {
    @Autowired
    ResignationApplicationRepository resignationApplicationRepository;
    public ResignationApplication addResignationApplication(ResignationApplication resignationApplication) {
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
}
