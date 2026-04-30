package ca.openbox.user.repository;

import ca.openbox.user.dataobject.UserDO;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<UserDO,String> {
    UserDO getUserDOByUsernameAndActiveIsTrue(String username);
    UserDO getUserDOByEmailAndActiveIsTrue(String email);
    UserDO save(UserDO userDO);
    UserDO findByUsername(String username);
    UserDO getUserDOByEmail(String email);
    boolean existsByUsername(String username);

}
