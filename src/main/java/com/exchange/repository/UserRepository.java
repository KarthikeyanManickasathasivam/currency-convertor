package com.exchange.repository;

import com.exchange.model.User;
import com.exchange.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.isActive = true")
    long countActiveByRole(Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActive();
}
