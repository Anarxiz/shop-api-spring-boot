package com.deliver.shop.application.contracts.auth;

import com.deliver.shop.domain.entities.UserEntity;
import com.deliver.shop.domain.enums.Role;

import java.util.UUID;

public record UserResponse(UUID id, String firstName, String lastName, String email, String phone, Role role) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), user.getRole());
    }
}
