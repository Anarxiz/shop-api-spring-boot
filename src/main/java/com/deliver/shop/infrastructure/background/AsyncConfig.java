package com.deliver.shop.infrastructure.background;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Включает @Async (фоновая обработка событий, уведомления). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
