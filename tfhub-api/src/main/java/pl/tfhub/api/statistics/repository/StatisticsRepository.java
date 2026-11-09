package pl.tfhub.api.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.statistics.dto.response.AvailableTestResponse;
import pl.tfhub.api.statistics.dto.response.RecentResultResponse;
import pl.tfhub.api.test.enums.TestCategory;

@Repository
public class StatisticsRepository {

	private static final String CLUB_RECORDS_SQL = """
			WITH athlete_results AS (
				SELECT
					a.id AS athlete_id,
					a.first_name,
					a.last_name,
					t.higher_is_better,
					t.unit,
					r.value,
					r.result_date,
					r.id AS result_id,
					ROW_NUMBER() OVER (
						PARTITION BY a.id
						ORDER BY
							CASE WHEN t.higher_is_better THEN r.value END DESC,
							CASE WHEN NOT t.higher_is_better THEN r.value END ASC,
							r.result_date DESC,
							r.id DESC
					) AS athlete_result_rank
				FROM test_results r
				JOIN athletes a ON a.id = r.athlete_id
				JOIN tests t ON t.id = r.test_id
				WHERE r.test_id = :testId
				  AND a.gender = :gender
				%s%s
			),
			best_results AS (
				SELECT *
				FROM athlete_results
				WHERE athlete_result_rank = 1
			),
			ranked_results AS (
				SELECT
					*,
					RANK() OVER (
						ORDER BY
							CASE WHEN higher_is_better THEN value END DESC,
							CASE WHEN NOT higher_is_better THEN value END ASC
					) AS place
				FROM best_results
			)
			SELECT
				place,
				athlete_id,
				first_name || ' ' || last_name AS athlete_name,
				value,
				unit,
				result_date
			FROM ranked_results
			ORDER BY place, last_name, first_name, athlete_id
			""";

	private final JdbcClient jdbcClient;

	public StatisticsRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public long countActiveAthletes() {
		return countActiveAthletes(null);
	}

	public long countActiveAthletes(List<Long> groupIds) {
		String groupFilter = groupIds == null ? "" : " AND a.training_group_id IN (:groupIds)";
		JdbcClient.StatementSpec statement = jdbcClient.sql(
				"SELECT COUNT(*) FROM athletes a WHERE a.active = TRUE" + groupFilter);
		statement = withGroups(statement, groupIds);
		return statement.query(Long.class).single();
	}

	public List<AvailableTestResponse> findAvailableTests() {
		return findAvailableTests(null);
	}

	public List<AvailableTestResponse> findAvailableTests(List<Long> groupIds) {
		String groupFilter = groupIds == null ? "" : " AND a.training_group_id IN (:groupIds)";
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
						SELECT id, name, category, unit, active
						FROM (
							SELECT DISTINCT t.id, t.name, t.category, t.unit, t.active
							FROM tests t
							JOIN test_results r ON r.test_id = t.id
							JOIN athletes a ON a.id = r.athlete_id
							WHERE TRUE
							%s
						) available_tests
						ORDER BY LOWER(name), name, id
						""".formatted(groupFilter));
		statement = withGroups(statement, groupIds);
		return statement.query((resultSet, rowNumber) -> new AvailableTestResponse(
						resultSet.getLong("id"),
						resultSet.getString("name"),
						TestCategory.valueOf(resultSet.getString("category")),
						resultSet.getString("unit"),
						resultSet.getBoolean("active")))
				.list();
	}

	public List<Integer> findAvailableSeasons() {
		return findAvailableSeasons(null);
	}

	public List<Integer> findAvailableSeasons(List<Long> groupIds) {
		String groupFilter = groupIds == null ? "" : " AND a.training_group_id IN (:groupIds)";
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
						SELECT DISTINCT EXTRACT(YEAR FROM result_date)::INTEGER AS season
						FROM test_results r
						JOIN athletes a ON a.id = r.athlete_id
						WHERE TRUE
						%s
						ORDER BY season DESC
						""".formatted(groupFilter));
		statement = withGroups(statement, groupIds);
		return statement.query(Integer.class).list();
	}

	public List<RecentResultResponse> findRecentResults() {
		return findRecentResults(null);
	}

	public List<RecentResultResponse> findRecentResults(List<Long> groupIds) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
						SELECT
							r.id,
							r.result_date,
							a.id AS athlete_id,
							a.first_name || ' ' || a.last_name AS athlete_name,
							t.id AS test_id,
							t.name AS test_name,
							r.value,
							t.unit,
							r.type
						FROM test_results r
						JOIN athletes a ON a.id = r.athlete_id
						JOIN tests t ON t.id = r.test_id
						%s
						ORDER BY r.result_date DESC, r.id DESC
						LIMIT 10
						""".formatted(groupIds == null ? "" : "WHERE a.training_group_id IN (:groupIds)"));
		statement = withGroups(statement, groupIds);
		return statement.query((resultSet, rowNumber) -> new RecentResultResponse(
						resultSet.getLong("id"),
						resultSet.getObject("result_date", LocalDate.class),
						resultSet.getLong("athlete_id"),
						resultSet.getString("athlete_name"),
						resultSet.getLong("test_id"),
						resultSet.getString("test_name"),
						resultSet.getBigDecimal("value"),
						resultSet.getString("unit"),
						ResultType.valueOf(resultSet.getString("type"))))
				.list();
	}

	public boolean existsTest(long testId) {
		return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM tests WHERE id = :testId)")
				.param("testId", testId)
				.query(Boolean.class)
				.single();
	}

	public List<ClubRecordRow> findClubRecords(long testId, Integer season, Gender gender) {
		return findClubRecords(testId, season, gender, null);
	}

	public List<ClubRecordRow> findClubRecords(
			long testId,
			Integer season,
			Gender gender,
			List<Long> groupIds) {
		String seasonFilter = season == null ? "" : "AND EXTRACT(YEAR FROM r.result_date)::INTEGER = :season";
		String groupFilter = groupIds == null ? "" : "AND a.training_group_id IN (:groupIds)";
		JdbcClient.StatementSpec statement = jdbcClient.sql(CLUB_RECORDS_SQL.formatted(seasonFilter, groupFilter))
				.param("testId", testId)
				.param("gender", gender.name());
		if (season != null) {
			statement = statement.param("season", season);
		}
		statement = withGroups(statement, groupIds);

		return statement.query((resultSet, rowNumber) -> new ClubRecordRow(
				resultSet.getInt("place"),
				resultSet.getLong("athlete_id"),
				resultSet.getString("athlete_name"),
				resultSet.getBigDecimal("value"),
				resultSet.getString("unit"),
				resultSet.getObject("result_date", LocalDate.class)))
				.list();
	}

	private JdbcClient.StatementSpec withGroups(JdbcClient.StatementSpec statement, List<Long> groupIds) {
		return groupIds == null ? statement : statement.param("groupIds", groupIds);
	}
}
