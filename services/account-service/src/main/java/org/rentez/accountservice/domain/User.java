package org.rentez.accountservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A platform account. One table serves customers, staff and admins.
 *
 * <p>This is the root of the account domain and has <strong>no outbound
 * relationships at all</strong> - which is what let it be extracted first. Other
 * services refer to a user by the raw {@code id} carried in the JWT, never by an
 * object reference, so nothing outside this service can load a User.
 *
 * <p>{@code passwordHash} is intentionally package-visible on the way out: there
 * is no getter used by any web layer, and controllers return
 * {@code UserResponse} rather than this entity. In the monolith this class was
 * serialised straight into API responses with a public {@code getPasswordHash()},
 * which put every BCrypt hash in the body of {@code GET /api/users/me} and
 * {@code GET /api/admin/users}.
 */
@Entity
@Table(name = "app_user")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 72)
	private String passwordHash;

	@Column(length = 20)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Role role = Role.CUSTOMER;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected User() {
	}

	public User(String fullName, String email, String passwordHash, String phone, Role role) {
		this.fullName = fullName;
		this.email = email;
		this.passwordHash = passwordHash;
		this.phone = phone;
		this.role = role;
	}

	public Long getId() { return id; }
	public String getFullName() { return fullName; }
	public void setFullName(String fullName) { this.fullName = fullName; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public Role getRole() { return role; }
	public void setRole(Role role) { this.role = role; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public Instant getCreatedAt() { return createdAt; }
}
