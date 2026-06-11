package com.exchange.controller;

import com.exchange.dto.response.UserResponse;
import com.exchange.model.User;
import com.exchange.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Users", description = "User profile management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user's profile")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getUserById(user.getUserId()));
    }

    @Operation(summary = "Update current user's profile (username and/or password)")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.updateProfile(
                user.getUserId(),
                body.get("username"),
                body.get("password")));
    }
}
