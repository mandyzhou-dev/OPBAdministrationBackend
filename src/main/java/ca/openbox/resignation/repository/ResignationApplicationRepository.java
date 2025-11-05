package ca.openbox.resignation.repository;

import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.resignation.entities.ResignationApplicationStatus;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface ResignationApplicationRepository extends Repository<ResignationApplication,Integer> {
    ResignationApplication save(ResignationApplication resignationApplication);
    ResignationApplication getResignationApplicationById(Integer id);
    List<ResignationApplication> findAll();
    ResignationApplication getResignationApplicationByStatusIsContainingOrderBySubmittedAtDesc(String status);
    void deleteById(Integer id);
    boolean existsByApplicantAndStatusIn(String username, List<ResignationApplicationStatus> status);
}
