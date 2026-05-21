package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.repository.LeaveApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class LeaveApplicationService {
    private static final String PENDING_STATUS = "pending";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("submitTime", "start");

    @Autowired
    LeaveApplicationRepository leaveApplicationRepository;
    @Autowired
    ApplicationHistoryAccessPolicy applicationHistoryAccessPolicy;
    public LeaveApplication addLeaveApplication(LeaveApplication leaveApplication){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.save(leaveApplication.toDO());
        return LeaveApplication.fromDO(leaveApplicationDO);
    }
    public void permitApplication(Integer applicationID){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setStatus("approved");
        leaveApplicationDO.setCurrentHandler(leaveApplicationDO.getApplicant());
        leaveApplicationRepository.save(leaveApplicationDO);
    }
    public void rejectApplication(Integer applicationID, String rejectReason){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setStatus("rejected");
        leaveApplicationDO.setRejectReason(rejectReason);
        leaveApplicationDO.setCurrentHandler(leaveApplicationDO.getApplicant());
        leaveApplicationRepository.save(leaveApplicationDO);
    }
    public void deleteApplication(Integer applicationID){
        leaveApplicationRepository.deleteById(applicationID);
    }
    public List<LeaveApplication> getApplicationsByHandler(String handler){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
       // leaveApplicationRepository.getLeaveApplication
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(handler);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public List<LeaveApplication> getApplicationsByApplicant(String applicant){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
        // leaveApplicationRepository.getLeaveApplication
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc(applicant);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public List<LeaveApplication> getAllApplications(){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc(PENDING_STATUS);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public PageResponseDTO<LeaveApplication> getHistory(String employeeUsername, int page, int size, String sort, String operatorUsername){
        applicationHistoryAccessPolicy.resolveVisibility(operatorUsername);
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), toSort(sort));
        String normalizedEmployee = normalizeEmployee(employeeUsername);

        Page<LeaveApplicationDO> applicationPage;
        if (normalizedEmployee == null) {
            applicationPage = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(PENDING_STATUS, pageable);
        } else {
            applicationPage = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingAndApplicant(PENDING_STATUS, normalizedEmployee, pageable);
        }

        List<LeaveApplication> applications = applicationPage.getContent().stream()
                .map(LeaveApplication::fromDO)
                .toList();
        return new PageResponseDTO<>(
                applications,
                applicationPage.getNumber(),
                applicationPage.getSize(),
                applicationPage.getTotalElements(),
                applicationPage.getTotalPages(),
                toSortParameter(pageable.getSort())
        );
    }
    public void addNoteToApplication(Integer applicationID, String note){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setNote(note);
        leaveApplicationRepository.save(leaveApplicationDO);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeEmployee(String employeeUsername) {
        if (employeeUsername == null || employeeUsername.isBlank()) {
            return null;
        }
        return employeeUsername.trim();
    }

    private Sort toSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "submitTime";
        String direction = parts.length > 1 ? parts[1].trim() : "desc";
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "submitTime";
            direction = "desc";
        }
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sortDirection, field);
    }

    private String toSortParameter(Sort sort) {
        Sort.Order order = sort.iterator().next();
        return order.getProperty() + "," + order.getDirection().name().toLowerCase();
    }
}
