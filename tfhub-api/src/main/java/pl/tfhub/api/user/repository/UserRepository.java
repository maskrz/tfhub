package pl.tfhub.api.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	@Query("SELECT u FROM User u WHERE lower(trim(u.username)) = lower(trim(:username))")
	Optional<User> findByNormalizedUsername(@Param("username") String username);
}
