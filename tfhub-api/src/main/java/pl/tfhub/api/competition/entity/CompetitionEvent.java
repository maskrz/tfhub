package pl.tfhub.api.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "competition_events")
public class CompetitionEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 10)
	private String unit;

	@Column(nullable = false)
	private boolean active = true;

	protected CompetitionEvent() {
	}

	public CompetitionEvent(String name, String unit) {
		this.name = name;
		this.unit = unit;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getUnit() {
		return unit;
	}

	public boolean isActive() {
		return active;
	}

	public void update(String name, String unit) {
		this.name = name;
		this.unit = unit;
	}

	public void changeStatus(boolean active) {
		this.active = active;
	}
}
