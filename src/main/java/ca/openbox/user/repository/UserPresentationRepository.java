package ca.openbox.user.repository;

import ca.openbox.user.presentation.UserPresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface UserPresentationRepository extends JpaRepository<UserPresentation, String> {
    public Collection<UserPresentation> findByRolesContainingOrderByActiveDesc(String role);
    public List<UserPresentation> findByRolesLikeAndActiveIsTrue(String role);
    public Collection<UserPresentation> findByGroupNameLikeAndActiveIsTrue(String groupName);
}
