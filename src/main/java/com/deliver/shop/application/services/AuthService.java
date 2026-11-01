package com.deliver.shop.application.services;

import com.deliver.shop.application.contracts.auth.*;
import com.deliver.shop.application.exceptions.BusinessException;
import com.deliver.shop.application.exceptions.NotFoundException;
import com.deliver.shop.application.exceptions.UnauthorizedException;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.domain.entities.UserEntity;
import com.deliver.shop.domain.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BAD_CREDENTIALS = "Неверный логин или пароль";
    private static final String USER_NOT_FOUND = "Пользователь не найден";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email()).orElseThrow(() -> {
            log.warn("Попытка входа с несуществующим email {}", request.email());
            return new UnauthorizedException(BAD_CREDENTIALS);
        });

        if (!passwordHasher.verify(request.password(), user.getPasswordHash(), user.getPasswordSalt())) {
            log.warn("Попытка входа с неверным паролем для {}", request.email());
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        String token = tokenService.generateToken(user.getId(), user.getEmail(), user.getRole());
        log.info("Пользователь {} успешно вошёл", user.getId());
        return new AuthResponse(user.getId(), user.getFirstName(), user.getEmail(), token);
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Попытка регистрации на занятый email {}", request.email());
            throw new BusinessException("Email уже занят");
        }
        if (userRepository.existsByPhone(request.phone())) {
            log.warn("Попытка регистрации на занятый телефон {}", request.phone());
            throw new BusinessException("Телефон уже занят");
        }

        PasswordHasher.HashedPassword password = passwordHasher.hash(request.password());

        UserEntity user = new UserEntity();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName() == null ? "" : request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(Role.USER);
        user.setPasswordHash(password.hash());
        user.setPasswordSalt(password.salt());
        userRepository.save(user);

        log.info("Зарегистрирован новый пользователь {}", user.getId());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Пользователя не существует {}", userId);
            return new NotFoundException(USER_NOT_FOUND);
        });

        if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash(), user.getPasswordSalt())) {
            log.warn("Неверный текущий пароль при смене пароля для {}", userId);
            throw new BusinessException("Неверный пароль");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException("Не должен быть такой же как прошлый пароль");
        }

        PasswordHasher.HashedPassword password = passwordHasher.hash(request.newPassword());
        user.setPasswordHash(password.hash());
        user.setPasswordSalt(password.salt());
        log.info("Пользователь {} сменил пароль", userId);
    }

    @Transactional
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Пользователя не существует {}", userId);
            return new NotFoundException("Пользователя не существует");
        });

        if (!request.phone().equals(user.getPhone()) && userRepository.existsByPhone(request.phone())) {
            log.warn("Попытка занять телефон {}, уже используемый другим пользователем", request.phone());
            throw new BusinessException("Телефон уже занят");
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName() == null ? "" : request.lastName());
        user.setPhone(request.phone());
        log.info("Пользователь {} сменил данные аккаунта", userId);
    }
}
