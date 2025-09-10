package ca.openbox.user.repository;

import ca.openbox.user.presentation.UserPresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface UserPresentationRepository extends JpaRepository<UserPresentation, String> {
    /*@Query(value = "select username from opb.opb_user where roles like '%tester%'",nativeQuery = true

    )
    public Collection<UserPresentation> getUserPresentationByRole(String role);*/
    public Collection<UserPresentation> findByRolesLikeAndActiveIsTrue(String role);
    public Collection<UserPresentation> findByGroupNameLikeAndActiveIsTrue(String groupName);
}
