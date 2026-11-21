package com.terp.erp.employee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Nightly jobs (warning expiry). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
