package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

/** Profile self-service plus admin account management. */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final AuditService auditService;

	public UserService(UserRepository userRepository, AuditService auditService) {
		this.userRepository = userRepository;
		this.auditService = auditService;
	}

	public User updateProfile(User current, UpdateProfileRequest request) {
		current.setFullName(request.fullName());
		current.setPhone(request.phone());
		User saved = userRepository.save(current);
		auditService.log(current.getEmail(), "UPDATE_PROFILE", "User", current.getId(), null);
		return saved;
	}

	public List<User> listAll() {
		return userRepository.findAll();
	}

	public User setEnabled(Long userId, boolean enabled, String actorEmail) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such user"));
		user.setEnabled(enabled);
		User saved = userRepository.save(user);
		auditService.log(actorEmail, enabled ? "ENABLE_USER" : "DISABLE_USER", "User", userId, null);
		return saved;
	}

	public User setRole(Long userId, User.Role role, String actorEmail) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such user"));
		user.setRole(role);
		User saved = userRepository.save(user);
		auditService.log(actorEmail, "CHANGE_ROLE", "User", userId, "New role: " + role);
		return saved;
	}
}
