package com.deliver.shop.api.controllers;

import com.deliver.shop.api.security.CurrentUser;
import com.deliver.shop.application.contracts.auth.*;
import com.deliver.shop.application.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/change-password")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(jwt), request);
    }

    @PatchMapping("/profile")
    public void updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        authService.updateProfile(CurrentUser.id(jwt), request);
    }
}
