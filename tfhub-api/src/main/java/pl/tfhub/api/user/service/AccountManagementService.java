package pl.tfhub.api.user.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.service.TrainingGroupService;
import pl.tfhub.api.user.dto.request.CreateAccountRequest;
import pl.tfhub.api.user.dto.request.ResetAccountPasswordRequest;
import pl.tfhub.api.user.dto.request.UpdateAccountRequest;
import pl.tfhub.api.user.dto.response.AccountResponse;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.exception.AccountNotFoundException;
import pl.tfhub.api.user.exception.DuplicateAthleteAccountException;
import pl.tfhub.api.user.exception.DuplicateUsernameException;
import pl.tfhub.api.user.exception.InvalidAccountAssignmentException;
import pl.tfhub.api.user.exception.LastActiveAdministratorException;
import pl.tfhub.api.user.repository.UserRepository;

@Service
public class AccountManagementService {

	private static final long ACCOUNT_MUTATION_LOCK = 402_001_001L;

	private final UserRepository userRepository;
	private final TrainingGroupService trainingGroupService;
	private final AthleteService athleteService;
	private final JdbcTemplate jdbcTemplate;
	private final JdbcClient jdbcClient;
	private final PasswordEncoder passwordEncoder;

	public AccountManagementService(
			UserRepository userRepository,
			TrainingGroupService trainingGroupService,
			AthleteService athleteService,
			JdbcTemplate jdbcTemplate,
			JdbcClient jdbcClient,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.trainingGroupService = trainingGroupService;
		this.athleteService = athleteService;
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcClient = jdbcClient;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> listAccounts(String username, UserRole role, Boolean active) {
		String filter = username == null || username.isBlank() ? null : username.trim();
		StringBuilder query = new StringBuilder("""
				SELECT id, username, role, active
				FROM users
				WHERE TRUE
				""");
		if (filter != null) {
			query.append(" AND lower(trim(username)) LIKE '%' || lower(:username) || '%'");
		}
		if (role != null) {
			query.append(" AND role = :role");
		}
		if (active != null) {
			query.append(" AND active = :active");
		}
		query.append(" ORDER BY id");
		var accountQuery = jdbcClient.sql(query.toString());
		if (filter != null) {
			accountQuery.param("username", filter);
		}
		if (role != null) {
			accountQuery.param("role", role.name());
		}
		if (active != null) {
			accountQuery.param("active", active);
		}
		List<AccountResponse> accounts = accountQuery
				.query((resultSet, rowNumber) -> new AccountResponse(
						resultSet.getLong("id"),
						resultSet.getString("username"),
						UserRole.valueOf(resultSet.getString("role")),
						resultSet.getBoolean("active"),
						List.of(),
						null))
				.list();
		if (accounts.isEmpty()) {
			return accounts;
		}

		List<Long> accountIds = accounts.stream().map(AccountResponse::id).toList();
		Map<Long, List<AccountResponse.TrainingGroupAssignment>> groups = new HashMap<>();
		jdbcClient.sql("""
						SELECT utg.user_id, tg.id, tg.name
						FROM user_training_group utg
						JOIN training_groups tg ON tg.id = utg.training_group_id
						WHERE utg.user_id IN (:accountIds)
						ORDER BY tg.id
						""")
				.param("accountIds", accountIds)
				.query((resultSet, rowNumber) -> Map.entry(
						resultSet.getLong("user_id"),
						new AccountResponse.TrainingGroupAssignment(
								resultSet.getLong("id"), resultSet.getString("name"))))
				.list()
				.forEach(entry -> groups.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
						.add(entry.getValue()));

		Map<Long, AccountResponse.AthleteAssignment> athletes = new HashMap<>();
		jdbcClient.sql("""
						SELECT uaa.user_id, a.id, a.first_name, a.last_name, a.birth_year,
							a.training_group_id, tg.name AS training_group_name
						FROM user_athlete_access uaa
						JOIN athletes a ON a.id = uaa.athlete_id
						LEFT JOIN training_groups tg ON tg.id = a.training_group_id
						WHERE uaa.user_id IN (:accountIds)
						ORDER BY a.id
						""")
				.param("accountIds", accountIds)
				.query((resultSet, rowNumber) -> Map.entry(
						resultSet.getLong("user_id"),
						new AccountResponse.AthleteAssignment(
								resultSet.getLong("id"),
								resultSet.getString("first_name"),
								resultSet.getString("last_name"),
								resultSet.getInt("birth_year"),
								(resultSet.getObject("training_group_id", Long.class)),
								resultSet.getString("training_group_name"))))
				.list()
				.forEach(entry -> athletes.put(entry.getKey(), entry.getValue()));

		return accounts.stream()
				.map(account -> new AccountResponse(
						account.id(),
						account.username(),
						account.role(),
						account.active(),
						account.role() == UserRole.COACH
								? List.copyOf(groups.getOrDefault(account.id(), List.of()))
								: List.of(),
						account.role() == UserRole.ATHLETE ? athletes.get(account.id()) : null))
				.toList();
	}

	@Transactional
	public AccountResponse createAccount(CreateAccountRequest request) {
		String username = normalizeUsername(request.username());
		String passwordHash = passwordEncoder.encode(request.password());
		lockAccountMutations();
		if (userRepository.existsByNormalizedUsername(username)) {
			throw new DuplicateUsernameException();
		}
		Assignments assignments = validateAssignments(
				request.role(), request.trainingGroupIds(), request.athleteId(), null);

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		User user = userRepository.saveAndFlush(new User(username, passwordHash, request.role(), now));
		replaceAssignments(user.getId(), assignments);
		ensureActiveAdministrator();
		return listAccountsById(user.getId());
	}

	@Transactional
	public AccountResponse updateAccount(long id, UpdateAccountRequest request) {
		String username = normalizeUsername(request.username());
		lockAccountMutations();
		User user = getAccount(id);
		if (userRepository.existsByNormalizedUsernameAndIdNot(username, id)) {
			throw new DuplicateUsernameException();
		}
		Assignments assignments = validateAssignments(
				request.role(), request.trainingGroupIds(), request.athleteId(), id);

		user.updateAccount(username, request.role());
		userRepository.flush();
		replaceAssignments(id, assignments);
		ensureActiveAdministrator();
		return listAccountsById(id);
	}

	@Transactional
	public AccountResponse changeStatus(long id, boolean active) {
		lockAccountMutations();
		User user = getAccount(id);
		user.changeStatus(active);
		userRepository.flush();
		ensureActiveAdministrator();
		return listAccountsById(id);
	}

	@Transactional
	public void resetPassword(long id, ResetAccountPasswordRequest request) {
		String passwordHash = passwordEncoder.encode(request.newPassword());
		lockAccountMutations();
		User user = getAccount(id);
		user.changePassword(passwordHash, OffsetDateTime.now(ZoneOffset.UTC));
		userRepository.flush();
		ensureActiveAdministrator();
	}

	private AccountResponse listAccountsById(long id) {
		return listAccounts(null, null, null).stream()
				.filter(account -> account.id() == id)
				.findFirst()
				.orElseThrow(() -> new AccountNotFoundException(id));
	}

	private User getAccount(long id) {
		return userRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
	}

	private Assignments validateAssignments(
			UserRole role,
			List<Long> requestedGroupIds,
			Long athleteId,
			Long currentUserId) {
		List<Long> groupIds = requestedGroupIds == null ? List.of() : List.copyOf(requestedGroupIds);
		if (new HashSet<>(groupIds).size() != groupIds.size()) {
			throw new InvalidAccountAssignmentException("Training group IDs must be unique");
		}
		switch (role) {
			case ADMIN -> {
				if (!groupIds.isEmpty() || athleteId != null) {
					throw new InvalidAccountAssignmentException("ADMIN accounts cannot have assignments");
				}
			}
			case COACH -> {
				if (groupIds.isEmpty() || athleteId != null) {
					throw new InvalidAccountAssignmentException(
							"COACH accounts require training groups and cannot have an athlete assignment");
				}
			}
			case ATHLETE -> {
				if (!groupIds.isEmpty() || athleteId == null) {
					throw new InvalidAccountAssignmentException(
							"ATHLETE accounts require exactly one athlete and cannot have training group assignments");
				}
			}
		}

		Set<Long> foundIds = trainingGroupService.findAccessibleGroups().stream()
				.map(group -> group.id())
				.collect(java.util.stream.Collectors.toSet());
		if (!foundIds.containsAll(groupIds)) {
			throw new TrainingGroupNotFoundException(groupIds.stream()
					.filter(groupId -> !foundIds.contains(groupId))
					.findFirst()
					.orElseThrow());
		}
		if (athleteId != null) {
			athleteService.getAthlete(athleteId);
			StringBuilder athleteAssignmentQuery = new StringBuilder("""
					SELECT EXISTS (
						SELECT 1
						FROM user_athlete_access
						WHERE athlete_id = :athleteId
					""");
			if (currentUserId != null) {
				athleteAssignmentQuery.append(" AND user_id <> :currentUserId");
			}
			athleteAssignmentQuery.append(")");
			var athleteAssignmentCheck = jdbcClient.sql(athleteAssignmentQuery.toString())
					.param("athleteId", athleteId);
			if (currentUserId != null) {
				athleteAssignmentCheck.param("currentUserId", currentUserId);
			}
			boolean alreadyAssigned = athleteAssignmentCheck
					.query(Boolean.class)
					.single();
			if (alreadyAssigned) {
				throw new DuplicateAthleteAccountException();
			}
		}
		return new Assignments(groupIds, athleteId);
	}

	private void replaceAssignments(long userId, Assignments assignments) {
		jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM user_athlete_access WHERE user_id = ?", userId);
		for (Long groupId : assignments.groupIds()) {
			jdbcTemplate.update(
					"INSERT INTO user_training_group (user_id, training_group_id) VALUES (?, ?)",
					userId, groupId);
		}
		if (assignments.athleteId() != null) {
			jdbcTemplate.update(
					"INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (?, ?)",
					userId, assignments.athleteId());
		}
	}

	private void lockAccountMutations() {
		jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + ACCOUNT_MUTATION_LOCK + ")");
	}

	private void ensureActiveAdministrator() {
		Long activeAdministrators = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = TRUE",
				Long.class);
		if (activeAdministrators == null || activeAdministrators == 0) {
			throw new LastActiveAdministratorException();
		}
	}

	private String normalizeUsername(String username) {
		return username.trim();
	}

	private record Assignments(List<Long> groupIds, Long athleteId) {
	}
}
