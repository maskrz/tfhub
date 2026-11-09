package pl.tfhub.api.common.authorization;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.exception.InvalidAthleteGroupException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

@Service
public class AccessScopeService {

	private final JdbcClient jdbcClient;

	public AccessScopeService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public AuthenticatedUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new AccessDeniedException("An authenticated application user is required");
		}
		return user;
	}

	public boolean isAdmin() {
		return currentUser().role() == UserRole.ADMIN;
	}

	public void requireAdmin() {
		if (!isAdmin()) {
			throw new AccessDeniedException("Administrator access is required");
		}
	}

	public void requireCoachOrAdmin() {
		UserRole role = currentUser().role();
		if (role != UserRole.ADMIN && role != UserRole.COACH) {
			throw new AccessDeniedException("Coach or administrator access is required");
		}
	}

	public List<Long> assignedGroupIds() {
		AuthenticatedUser user = currentUser();
		if (user.role() != UserRole.COACH) {
			return List.of();
		}
		return jdbcClient.sql("""
						SELECT training_group_id
						FROM user_training_group
						WHERE user_id = :userId
						ORDER BY training_group_id
						""")
				.param("userId", user.id())
				.query(Long.class)
				.list();
	}

	public List<Long> effectiveGroupIds(List<Long> requestedGroupIds) {
		AuthenticatedUser user = currentUser();
		if (user.role() == UserRole.ADMIN) {
			if (requestedGroupIds == null || requestedGroupIds.isEmpty()) {
				return null;
			}
			return requestedGroupIds;
		}
		if (user.role() != UserRole.COACH) {
			return List.of(-1L);
		}

		List<Long> assigned = assignedGroupIds();
		if (requestedGroupIds == null || requestedGroupIds.isEmpty()) {
			return querySafeIds(assigned);
		}
		if (requestedGroupIds.stream().anyMatch(id -> !assigned.contains(id))) {
			throw new AccessDeniedException("Requested group is outside the authenticated user's scope");
		}
		return querySafeIds(requestedGroupIds);
	}

	public boolean canAccessAthlete(long athleteId) {
		AuthenticatedUser user = currentUser();
		if (user.role() == UserRole.ADMIN) {
			return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM athletes WHERE id = :athleteId)")
					.param("athleteId", athleteId).query(Boolean.class).single();
		}
		String query = user.role() == UserRole.COACH
				? """
					SELECT EXISTS (
						SELECT 1
						FROM athletes a
						JOIN user_training_group ug ON ug.training_group_id = a.training_group_id
						WHERE a.id = :athleteId AND ug.user_id = :userId
					)
					"""
				: """
					SELECT COUNT(*) = 1 AND COALESCE(BOOL_OR(athlete_id = :athleteId), FALSE)
						FROM user_athlete_access
						WHERE user_id = :userId
					""";
		return jdbcClient.sql(query)
				.param("athleteId", athleteId)
				.param("userId", user.id())
				.query(Boolean.class)
				.single();
	}

	public void requireAthleteAccess(long athleteId) {
		if (!canAccessAthlete(athleteId)) {
			throw new AthleteNotFoundException(athleteId);
		}
	}

	public void requireAthleteWriteAccess(long athleteId) {
		UserRole role = currentUser().role();
		if (role == UserRole.ATHLETE) {
			throw new AccessDeniedException("Athlete accounts are read-only");
		}
		requireAthleteAccess(athleteId);
	}

	public void requireGroupAssignment(long groupId) {
		AuthenticatedUser user = currentUser();
		if (user.role() == UserRole.ADMIN) {
			boolean exists = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM training_groups WHERE id = :groupId)")
					.param("groupId", groupId).query(Boolean.class).single();
			if (!exists) {
				throw new TrainingGroupNotFoundException(groupId);
			}
			return;
		}
		if (user.role() != UserRole.COACH || !assignedGroupIds().contains(groupId)) {
			throw new AccessDeniedException("Training group is outside the authenticated coach's assignments");
		}
	}

	public void requireGroupWhenMissing(boolean groupPresent) {
		if (!groupPresent && currentUser().role() == UserRole.COACH) {
			throw new InvalidAthleteGroupException();
		}
	}

	public void requireResultReadAccess(long resultId, long athleteId) {
		if (!canAccessAthlete(athleteId)) {
			throw new ResultNotFoundException(resultId);
		}
	}

	public List<Long> querySafeIds(List<Long> ids) {
		return ids.isEmpty() ? List.of(-1L) : new ArrayList<>(ids);
	}
}
