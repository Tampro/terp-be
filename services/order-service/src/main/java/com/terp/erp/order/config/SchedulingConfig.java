package com.terp.erp.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs CartCleanupJob; erp-common JobLockConfig (erp.jobs.lock.enabled) makes one instance run it at a time. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
