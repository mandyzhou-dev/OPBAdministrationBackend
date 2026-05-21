package ca.openbox.process.repository;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface LeaveApplicationRepository extends Repository<LeaveApplicationDO,Integer> {
    LeaveApplicationDO save(LeaveApplicationDO leaveApplicationDO);
    LeaveApplicationDO getLeaveApplicationDOById(Integer id);
    List<LeaveApplicationDO> getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(String currentHandler);
    List<LeaveApplicationDO> getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc(String applicant);
    List<LeaveApplicationDO> getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc(String status);
    Page<LeaveApplicationDO> getLeaveApplicationDOByStatusIsNotContaining(String status, Pageable pageable);
    Page<LeaveApplicationDO> getLeaveApplicationDOByStatusIsNotContainingAndApplicant(String status, String applicant, Pageable pageable);
    void deleteById(Integer id);
}
