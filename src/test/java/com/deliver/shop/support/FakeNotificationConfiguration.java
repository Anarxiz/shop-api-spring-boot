package com.deliver.shop.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class FakeNotificationConfiguration {

    @Bean
    public FakeNotificationChannel fakeNotificationChannel() {
        return new FakeNotificationChannel();
    }
}
