package pl.tfhub.api.athlete.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "athlete_aliases")
public class AthleteAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "athlete_id", nullable = false)
	private Athlete athlete;

	@Column(nullable = false, columnDefinition = "text")
	private String alias;

	@Column(name = "normalized_alias", insertable = false, updatable = false, columnDefinition = "text")
	private String normalizedAlias;

	protected AthleteAlias() {
	}

	public AthleteAlias(Athlete athlete, String alias) {
		this.athlete = athlete;
		this.alias = alias;
	}

	public Athlete getAthlete() {
		return athlete;
	}

	public String getAlias() {
		return alias;
	}

	public String getNormalizedAlias() {
		return normalizedAlias;
	}
}
