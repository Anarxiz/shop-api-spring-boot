package com.deliver.shop.infrastructure.notifications;

import com.deliver.shop.application.interfaces.notifications.NotificationChannel;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.domain.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Отправка писем. Включается, только если в application.yaml задан spring.mail.host
 * (иначе JavaMailSender не создаётся, и канал просто не регистрируется).
 */
@Component
@ConditionalOnProperty("spring.mail.host")
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${app.mail.sender-email:noreply@myservice.com}")
    private String senderEmail;

    @Override
    public String name() {
        return "email";
    }

    @Override
    public void send(UUID userId, String message) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Пользователь " + userId + " не найден"));

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(senderEmail);
        mail.setTo(user.getEmail());
        mail.setSubject("Обновление по заказу");
        mail.setText(message);
        mailSender.send(mail);
    }
}
