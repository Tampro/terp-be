package com.terp.erp.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Retry job of failed deliveries. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
