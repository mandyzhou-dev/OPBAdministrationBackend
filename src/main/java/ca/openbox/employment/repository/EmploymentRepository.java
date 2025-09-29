package ca.openbox.employment.repository;

import ca.openbox.employment.entities.Employment;
import org.springframework.data.repository.Repository;

public interface EmploymentRepository extends Repository<Employment,Integer> {
    Employment getEmploymentByUsername(String username);
    Employment save(Employment employment);
}
