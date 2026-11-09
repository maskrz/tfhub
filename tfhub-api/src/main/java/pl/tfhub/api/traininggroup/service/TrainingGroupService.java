package pl.tfhub.api.traininggroup.service;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingGroupService {

	private final JdbcClient jdbcClient;
	private final AccessScopeService accessScopeService;

	public TrainingGroupService(JdbcClient jdbcClient, AccessScopeService accessScopeService) {
		this.jdbcClient = jdbcClient;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public List<TrainingGroupResponse> findAccessibleGroups() {
		var user = accessScopeService.currentUser();
		if (user.role() == UserRole.ADMIN) {
			return jdbcClient.sql("SELECT id, name FROM training_groups ORDER BY lower(name), id")
					.query((rs, row) -> new TrainingGroupResponse(rs.getLong("id"), rs.getString("name")))
					.list();
		}
		if (user.role() != UserRole.COACH) {
			return List.of();
		}
		return jdbcClient.sql("""
						SELECT g.id, g.name
						FROM training_groups g
						JOIN user_training_group ug ON ug.training_group_id = g.id
						WHERE ug.user_id = :userId
						ORDER BY lower(g.name), g.id
						""")
				.param("userId", user.id())
				.query((rs, row) -> new TrainingGroupResponse(rs.getLong("id"), rs.getString("name")))
				.list();
	}
}
