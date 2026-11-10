package pl.tfhub.api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import pl.tfhub.api.user.enums.UserRole;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "password_changed_at", nullable = false)
	private OffsetDateTime passwordChangedAt;

	protected User() {
	}

	public User(String username, String passwordHash, UserRole role, OffsetDateTime createdAt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.active = true;
		this.createdAt = createdAt;
		this.passwordChangedAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public void updateAccount(String username, UserRole role) {
		this.username = username;
		this.role = role;
	}

	public void changeStatus(boolean active) {
		this.active = active;
	}

	public void changePassword(String passwordHash, OffsetDateTime passwordChangedAt) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = passwordChangedAt;
	}
}
