package com.deliver.shop.application.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки JWT из application.yaml */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secretKey,
        String issuer,
        String audience,
        long expiryInMinutes
) {
}
