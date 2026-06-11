package com.exchange.service;

import com.exchange.dto.response.UserResponse;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.model.User;
import com.exchange.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    public UserResponse getUserById(UUID id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID id, String username, String rawPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (username != null && !username.isBlank()) {
            user.setUsername(username);
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getDisplayUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
