package pl.tfhub.api.test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tests")
public class AthleticTest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 10)
	private String unit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TestCategory category;

	@Column(name = "higher_is_better", nullable = false)
	private boolean higherIsBetter;

	@Column(nullable = false)
	private boolean active = true;

	protected AthleticTest() {
	}

	public AthleticTest(String name, String unit, TestCategory category, boolean higherIsBetter) {
		this.name = name;
		this.unit = unit;
		this.category = category;
		this.higherIsBetter = higherIsBetter;
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

	public TestCategory getCategory() {
		return category;
	}

	public boolean isHigherIsBetter() {
		return higherIsBetter;
	}

	public boolean isActive() {
		return active;
	}
}
