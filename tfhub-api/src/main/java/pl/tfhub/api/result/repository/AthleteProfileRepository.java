package pl.tfhub.api.result.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.result.dto.response.AthleteResultHistoryResponse;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.test.enums.TestCategory;

@Repository
public class AthleteProfileRepository {

	private final JdbcClient jdbcClient;

	public AthleteProfileRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<TestResponse> findTestsWithResultsForAthlete(long athleteId) {
		return jdbcClient.sql("""
						SELECT
							t.id,
							t.name,
							t.unit,
							t.category,
							t.higher_is_better,
							t.active
						FROM tests t
						WHERE EXISTS (
							SELECT 1
							FROM test_results r
							WHERE r.test_id = t.id
							  AND r.athlete_id = :athleteId
						)
						ORDER BY LOWER(t.name), t.name, t.id
						""")
				.param("athleteId", athleteId)
				.query((resultSet, rowNumber) -> new TestResponse(
						resultSet.getLong("id"),
						resultSet.getString("name"),
						resultSet.getString("unit"),
						TestCategory.valueOf(resultSet.getString("category")),
						resultSet.getBoolean("higher_is_better"),
						resultSet.getBoolean("active")))
				.list();
	}

	public List<AthleteResultHistoryResponse> findResultHistory(
			long athleteId,
			List<Long> testIds,
			LocalDate fromDate,
			LocalDate toDate) {
		StringBuilder query = new StringBuilder("""
				SELECT
					r.id,
					t.id AS test_id,
					t.name AS test_name,
					t.unit AS test_unit,
					r.result_date,
					r.value,
					r.type
				FROM test_results r
				JOIN tests t ON t.id = r.test_id
				WHERE r.athlete_id = :athleteId
				""");

		if (testIds != null && !testIds.isEmpty()) {
			query.append(" AND r.test_id IN (:testIds)");
		}
		if (fromDate != null) {
			query.append(" AND r.result_date >= :fromDate");
		}
		if (toDate != null) {
			query.append(" AND r.result_date <= :toDate");
		}
		query.append(" ORDER BY r.result_date ASC, r.test_id ASC, r.id ASC");

		JdbcClient.StatementSpec statement = jdbcClient.sql(query.toString()).param("athleteId", athleteId);
		if (testIds != null && !testIds.isEmpty()) {
			statement = statement.param("testIds", testIds);
		}
		if (fromDate != null) {
			statement = statement.param("fromDate", fromDate);
		}
		if (toDate != null) {
			statement = statement.param("toDate", toDate);
		}

		return statement.query((resultSet, rowNumber) -> new AthleteResultHistoryResponse(
				resultSet.getLong("id"),
				resultSet.getLong("test_id"),
				resultSet.getString("test_name"),
				resultSet.getString("test_unit"),
				resultSet.getObject("result_date", LocalDate.class),
				resultSet.getBigDecimal("value"),
				ResultType.valueOf(resultSet.getString("type"))))
				.list();
	}
}
