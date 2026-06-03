package ca.openbox.process.repository;

import ca.openbox.process.dataobject.LeaveApplicationProofDO;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface LeaveApplicationProofRepository extends Repository<LeaveApplicationProofDO, Integer> {
    LeaveApplicationProofDO save(LeaveApplicationProofDO leaveApplicationProofDO);
    Optional<LeaveApplicationProofDO> findById(Integer applicationId);
    List<LeaveApplicationProofDO> findByApplicationIdIn(List<Integer> applicationIds);
}
