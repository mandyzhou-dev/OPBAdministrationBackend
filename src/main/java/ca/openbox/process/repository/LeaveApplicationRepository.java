package ca.openbox.process.repository;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface LeaveApplicationRepository extends Repository<LeaveApplicationDO,Integer> {
    LeaveApplicationDO save(LeaveApplicationDO leaveApplicationDO);
    LeaveApplicationDO getLeaveApplicationDOById(Integer id);
    List<LeaveApplicationDO> getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(String currentHandler);
    List<LeaveApplicationDO> getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc(String applicant);
    List<LeaveApplicationDO> getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc(String status);
    void deleteById(Integer id);
}
