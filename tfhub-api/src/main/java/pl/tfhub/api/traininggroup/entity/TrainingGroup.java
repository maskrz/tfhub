package pl.tfhub.api.traininggroup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_groups")
public class TrainingGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "coach_name", length = 150)
	private String coachName;

	protected TrainingGroup() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCoachName() {
		return coachName;
	}
}
