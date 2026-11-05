package pl.tfhub.api.athlete.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.tfhub.api.athlete.enums.Gender;

@Entity
@Table(name = "athletes")
public class Athlete {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "birth_year", nullable = false)
	private Integer birthYear;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Gender gender;

	@Column(nullable = false)
	private boolean active = true;

	protected Athlete() {
	}

	public Athlete(String firstName, String lastName, Integer birthYear, Gender gender) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.birthYear = birthYear;
		this.gender = gender;
	}

	public Long getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public Integer getBirthYear() {
		return birthYear;
	}

	public Gender getGender() {
		return gender;
	}

	public boolean isActive() {
		return active;
	}

	public void updatePersonalInformation(String firstName, String lastName, Integer birthYear, Gender gender) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.birthYear = birthYear;
		this.gender = gender;
	}

	public void changeStatus(boolean active) {
		this.active = active;
	}
}
