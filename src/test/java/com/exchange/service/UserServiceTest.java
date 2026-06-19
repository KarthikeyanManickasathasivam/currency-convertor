package com.exchange.service;

import com.exchange.dto.response.UserResponse;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$12$hashed")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    @Test
    void getUserById_existingUser_returnsResponse() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        UserResponse result = userService.getUserById(testUser.getUserId());

        assertThat(result.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getRole()).isEqualTo("USER");
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void getUserById_nonExistent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void deactivateUser_setsActiveToFalse() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deactivateUser(testUser.getUserId());

        assertThat(testUser.isActive()).isFalse();
        verify(userRepository).save(testUser);
    }

    @Test
    void deactivateUser_nonExistent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_usernameProvided_updatesUsername() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateProfile(testUser.getUserId(), "newname", null);

        // getDisplayUsername() returns the stored username field;
        // getUsername() is overridden by UserDetails to return email
        assertThat(testUser.getDisplayUsername()).isEqualTo("newname");
        assertThat(result.getUsername()).isEqualTo("newname");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateProfile_passwordProvided_encodesAndUpdates() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$12$newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(testUser.getUserId(), null, "NewPass1!");

        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newHash");
        verify(passwordEncoder).encode("NewPass1!");
    }

    @Test
    void updateProfile_blankUsername_doesNotUpdate() {
        String originalUsername = testUser.getUsername();
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(testUser.getUserId(), "   ", null);

        assertThat(testUser.getUsername()).isEqualTo(originalUsername);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateProfile_nullFields_noChanges() {
        String originalUsername = testUser.getUsername();
        String originalHash = testUser.getPasswordHash();
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(testUser.getUserId(), null, null);

        assertThat(testUser.getUsername()).isEqualTo(originalUsername);
        assertThat(testUser.getPasswordHash()).isEqualTo(originalHash);
        verifyNoInteractions(passwordEncoder);
    }
}
