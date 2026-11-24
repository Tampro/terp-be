package com.terp.erp.iam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The plan-expiry seat job (PlanExpirySeatJob); turned off with erp.saas.plan-expiry-seats.enabled=false. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
