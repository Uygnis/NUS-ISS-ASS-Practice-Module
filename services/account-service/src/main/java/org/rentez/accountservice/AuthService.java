package org.rentez.accountservice;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthException("Email already registered.");
        }

        User user = new User(email, request.getPassword(), request.getName().trim(), request.getPhone().trim());
        User savedUser = userRepository.save(user);
        return buildResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid email or password."));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new AuthException("Invalid email or password.");
        }
        return buildResponse(user);
    }

    public AuthResponse updateProfile(ProfileUpdateRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found."));

        user.setName(request.getName().trim());
        user.setPhone(request.getPhone().trim());
        User savedUser = userRepository.save(user);
        return buildResponse(savedUser);
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(user.getEmail(), user.getName(), user.getPhone());
    }
}
