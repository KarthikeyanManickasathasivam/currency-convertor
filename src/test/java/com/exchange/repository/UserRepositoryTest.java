package com.exchange.repository;

import com.exchange.model.User;
import com.exchange.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_existingUser_returnsUser() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$12$hash")
                .role(Role.USER)
                .isActive(true)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void existsByEmail_returnsTrue_whenExists() {
        User user = User.builder()
                .username("another")
                .email("another@example.com")
                .passwordHash("$2a$12$hash")
                .role(Role.USER)
                .isActive(true)
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("another@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    void countActive_countsOnlyActiveUsers() {
        userRepository.save(User.builder().username("active1").email("a1@e.com")
                .passwordHash("h").role(Role.USER).isActive(true).build());
        userRepository.save(User.builder().username("active2").email("a2@e.com")
                .passwordHash("h").role(Role.USER).isActive(true).build());
        User inactive = User.builder().username("inactive").email("i@e.com")
                .passwordHash("h").role(Role.USER).isActive(false).build();
        userRepository.save(inactive);

        assertThat(userRepository.countActive()).isEqualTo(2);
    }
}
