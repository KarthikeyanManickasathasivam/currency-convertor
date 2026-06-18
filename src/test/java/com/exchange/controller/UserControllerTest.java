package com.exchange.controller;

import com.exchange.dto.response.UserResponse;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.repository.UserRepository;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import com.exchange.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    private User testUser;
    private UsernamePasswordAuthenticationToken userAuth;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
        userAuth = new UsernamePasswordAuthenticationToken(
                testUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        userResponse = UserResponse.builder()
                .userId(testUser.getUserId())
                .username("testuser")
                .email("test@example.com")
                .role("USER")
                .isActive(true)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // With addFilters=false the SecurityContextHolderFilter never runs, so
    // @AuthenticationPrincipal reads SecurityContextHolder directly.
    private RequestPostProcessor withUser(UsernamePasswordAuthenticationToken auth) {
        return request -> {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            return request;
        };
    }

    @Test
    void getProfile_authenticatedUser_returns200() throws Exception {
        when(userService.getUserById(testUser.getUserId())).thenReturn(userResponse);

        mockMvc.perform(get("/api/users/me")
                        .with(withUser(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void updateProfile_newUsername_returns200() throws Exception {
        UserResponse updated = UserResponse.builder()
                .userId(testUser.getUserId())
                .username("newname")
                .email("test@example.com")
                .role("USER")
                .isActive(true)
                .build();

        when(userService.updateProfile(eq(testUser.getUserId()), eq("newname"), isNull()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .with(withUser(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "newname"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newname"));
    }

    @Test
    void updateProfile_emptyBody_returns200() throws Exception {
        when(userService.updateProfile(eq(testUser.getUserId()), isNull(), isNull()))
                .thenReturn(userResponse);

        mockMvc.perform(put("/api/users/me")
                        .with(withUser(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
