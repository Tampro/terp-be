package com.terp.erp.recruitment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Nightly jobs (offer expiry). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
