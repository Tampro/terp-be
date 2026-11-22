package com.terp.erp.attendance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The auto clock-out job (AutoClockOutService); turned off with erp.attendance.auto-clock-out.enabled=false. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
