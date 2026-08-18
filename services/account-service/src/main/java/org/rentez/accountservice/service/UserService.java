package org.rentez.accountservice.service;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.domain.User;
import org.rentez.accountservice.error.ApiException;
import org.rentez.accountservice.repository.AuditLogRepository;
import org.rentez.accountservice.repository.UserRepository;
import org.rentez.accountservice.web.dto.AuditLogResponse;
import org.rentez.accountservice.web.dto.UpdateProfileRequest;
import org.rentez.accountservice.web.dto.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Profile self-service plus admin account management. */
@Service
public class UserService {

	private static final int MAX_AUDIT_PAGE = 500;

	private final UserRepository userRepository;
	private final AuditLogRepository auditLogRepository;
	private final AuditService auditService;

	public UserService(UserRepository userRepository, AuditLogRepository auditLogRepository,
			AuditService auditService) {
		this.userRepository = userRepository;
		this.auditLogRepository = auditLogRepository;
		this.auditService = auditService;
	}

	/**
	 * Loads the caller's own row.
	 *
	 * <p>This is the one remaining lookup-by-id on the request path, and it is
	 * fine: it reads this service's own table to render a profile, rather than
	 * being a cross-service call to resolve who the caller is. Authorization
	 * never waits on it.
	 */
	@Transactional(readOnly = true)
	public UserResponse getById(Long userId) {
		return UserResponse.from(requireUser(userId));
	}

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = requireUser(userId);
		user.setFullName(request.fullName());
		user.setPhone(request.phone());
		User saved = userRepository.save(user);

		auditService.log(saved.getEmail(), "UPDATE_PROFILE", "User", saved.getId(), null);
		return UserResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listAll() {
		return userRepository.findAll().stream().map(UserResponse::from).toList();
	}

	@Transactional
	public UserResponse setEnabled(Long userId, boolean enabled, String actorEmail) {
		User user = requireUser(userId);
		user.setEnabled(enabled);
		User saved = userRepository.save(user);

		auditService.log(actorEmail, enabled ? "ENABLE_USER" : "DISABLE_USER", "User", userId, null);
		return UserResponse.from(saved);
	}

	@Transactional
	public UserResponse setRole(Long userId, Role role, String actorEmail) {
		User user = requireUser(userId);
		user.setRole(role);
		User saved = userRepository.save(user);

		auditService.log(actorEmail, "CHANGE_ROLE", "User", userId, "New role: " + role);
		return UserResponse.from(saved);
	}

	/** Most recent first, capped so an admin cannot ask for the whole table. */
	@Transactional(readOnly = true)
	public List<AuditLogResponse> recentAuditLog(int limit) {
		return auditLogRepository
				.findAllByOrderByOccurredAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_AUDIT_PAGE)))
				.stream()
				.map(AuditLogResponse::from)
				.toList();
	}

	private User requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such user"));
	}
}
