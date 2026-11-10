package pl.tfhub.api.backup;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;

@Repository
public class BackupExportRepository {

	private static final String EXPORT_SQL = """
			SELECT
				a.id AS athlete_id,
				a.first_name,
				a.last_name,
				a.birth_year,
				a.gender,
				a.active AS athlete_active,
				g.id AS training_group_id,
				g.name AS training_group_name,
				g.coach_name AS training_group_coach_name,
				t.id AS test_id,
				t.name AS test_name,
				t.category AS test_category,
				t.unit AS test_unit,
				t.higher_is_better,
				t.active AS test_active,
				r.id AS result_id,
				r.result_date,
				r.value AS result_value,
				r.type AS result_type
			FROM test_results r
			JOIN athletes a ON a.id = r.athlete_id
			LEFT JOIN training_groups g ON g.id = a.training_group_id
			JOIN tests t ON t.id = r.test_id
			ORDER BY r.id
			""";

	private final JdbcTemplate jdbcTemplate;

	public BackupExportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void forEachResult(Consumer<BackupExportRow> consumer) {
		PreparedStatementCreator statementCreator = connection -> {
			PreparedStatement statement = connection.prepareStatement(EXPORT_SQL);
			statement.setFetchSize(500);
			return statement;
		};
		jdbcTemplate.query(statementCreator, (ResultSet resultSet) -> consumer.accept(mapRow(resultSet)));
	}

	private BackupExportRow mapRow(ResultSet resultSet) throws SQLException {
		long groupId = resultSet.getLong("training_group_id");
		Long nullableGroupId = resultSet.wasNull() ? null : groupId;
		return new BackupExportRow(
				resultSet.getLong("athlete_id"),
				resultSet.getString("first_name"),
				resultSet.getString("last_name"),
				resultSet.getInt("birth_year"),
				resultSet.getString("gender"),
				resultSet.getBoolean("athlete_active"),
				nullableGroupId,
				resultSet.getString("training_group_name"),
				resultSet.getString("training_group_coach_name"),
				resultSet.getLong("test_id"),
				resultSet.getString("test_name"),
				resultSet.getString("test_category"),
				resultSet.getString("test_unit"),
				resultSet.getBoolean("higher_is_better"),
				resultSet.getBoolean("test_active"),
				resultSet.getLong("result_id"),
				resultSet.getObject("result_date", java.time.LocalDate.class),
				resultSet.getBigDecimal("result_value"),
				resultSet.getString("result_type"));
	}
}
