package pl.tfhub.api.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.result.enums.ResultType;

@Entity
@Table(name = "test_results")
public class TestResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "athlete_id", nullable = false)
	private Long athleteId;

	@Column(name = "test_id", nullable = false)
	private Long testId;

	@Column(name = "result_date", nullable = false)
	private LocalDate resultDate;

	@Column(nullable = false, precision = 12, scale = 3)
	private BigDecimal value;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ResultType type;

	protected TestResult() {
	}

	public TestResult(Long athleteId, Long testId, LocalDate resultDate, BigDecimal value, ResultType type) {
		this.athleteId = athleteId;
		this.testId = testId;
		this.resultDate = resultDate;
		this.value = value;
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public Long getAthleteId() {
		return athleteId;
	}

	public Long getTestId() {
		return testId;
	}

	public LocalDate getResultDate() {
		return resultDate;
	}

	public BigDecimal getValue() {
		return value;
	}

	public ResultType getType() {
		return type;
	}
}
