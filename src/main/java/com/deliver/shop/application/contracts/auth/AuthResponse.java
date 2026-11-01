package com.deliver.shop.application.contracts.auth;

import java.util.UUID;

public record AuthResponse(UUID userId, String firstName, String email, String token) {
}
