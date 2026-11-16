package pl.tfhub.api.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.competition.enums.CompetitionResultSource;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

@Entity
@Table(name = "competition_results")
public class CompetitionResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "athlete_id", nullable = false)
	private Long athleteId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private CompetitionEvent event;

	@Column(name = "result_date", nullable = false)
	private LocalDate resultDate;

	@Column(name = "original_mark", nullable = false, length = 50)
	private String originalMark;

	@Column(name = "numeric_value", precision = 12, scale = 3)
	private BigDecimal numericValue;

	@Column(nullable = false, length = 10)
	private String unit;

	@Column(precision = 6, scale = 2)
	private BigDecimal wind;

	private Integer season;

	@Enumerated(EnumType.STRING)
	@Column(name = "season_type", nullable = false, length = 20)
	private CompetitionSeasonType seasonType;

	@Column(name = "competition_name", length = 250)
	private String competitionName;

	@Column(length = 250)
	private String location;

	@Column(name = "result_round", length = 100)
	private String resultRound;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CompetitionResultSource source;

	@Column(name = "source_record_key", length = 255)
	private String sourceRecordKey;

	@Column(name = "source_fingerprint", length = 64)
	private String sourceFingerprint;

	protected CompetitionResult() {
	}

	public CompetitionResult(
			Long athleteId,
			CompetitionEvent event,
			LocalDate resultDate,
			String originalMark,
			BigDecimal numericValue,
			String unit,
			BigDecimal wind,
			Integer season,
			CompetitionSeasonType seasonType,
			String competitionName,
			String location,
			String resultRound,
			CompetitionResultSource source,
			String sourceRecordKey,
			String sourceFingerprint) {
		this.athleteId = athleteId;
		this.event = event;
		update(
				event, resultDate, originalMark, numericValue, unit, wind, season, seasonType,
				competitionName, location, resultRound, source, sourceRecordKey, sourceFingerprint);
	}

	public void update(
			CompetitionEvent event,
			LocalDate resultDate,
			String originalMark,
			BigDecimal numericValue,
			String unit,
			BigDecimal wind,
			Integer season,
			CompetitionSeasonType seasonType,
			String competitionName,
			String location,
			String resultRound,
			CompetitionResultSource source,
			String sourceRecordKey,
			String sourceFingerprint) {
		this.event = event;
		this.resultDate = resultDate;
		this.originalMark = originalMark;
		this.numericValue = numericValue;
		this.unit = unit;
		this.wind = wind;
		this.season = season;
		this.seasonType = seasonType;
		this.competitionName = competitionName;
		this.location = location;
		this.resultRound = resultRound;
		this.source = source;
		this.sourceRecordKey = sourceRecordKey;
		this.sourceFingerprint = sourceFingerprint;
	}

	public Long getId() {
		return id;
	}

	public Long getAthleteId() {
		return athleteId;
	}

	public CompetitionEvent getEvent() {
		return event;
	}

	public LocalDate getResultDate() {
		return resultDate;
	}

	public String getOriginalMark() {
		return originalMark;
	}

	public BigDecimal getNumericValue() {
		return numericValue;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getWind() {
		return wind;
	}

	public Integer getSeason() {
		return season;
	}

	public CompetitionSeasonType getSeasonType() {
		return seasonType;
	}

	public String getCompetitionName() {
		return competitionName;
	}

	public String getLocation() {
		return location;
	}

	public String getResultRound() {
		return resultRound;
	}

	public CompetitionResultSource getSource() {
		return source;
	}

	public String getSourceRecordKey() {
		return sourceRecordKey;
	}

	public String getSourceFingerprint() {
		return sourceFingerprint;
	}
}
