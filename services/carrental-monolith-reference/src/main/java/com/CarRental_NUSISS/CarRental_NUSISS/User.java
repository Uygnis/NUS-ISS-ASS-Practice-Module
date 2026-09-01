package com.CarRental_NUSISS.CarRental_NUSISS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A platform account. One table serves customers, staff and admins - the
 * {@link Role} column is what Spring Security uses to authorize requests.
 */
@Entity
@Table(name = "app_user")
public class User {

	public enum Role { CUSTOMER, STAFF, ADMIN }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String fullName;

	@Column(nullable = false, unique = true)
	private String email;

	/** BCrypt hash - never the raw password, and never serialized out to a client. */
	@JsonIgnore
	@Column(nullable = false)
	private String passwordHash;

	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.CUSTOMER;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false)
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
