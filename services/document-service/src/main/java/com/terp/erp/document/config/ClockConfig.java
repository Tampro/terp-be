package com.terp.erp.document.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Injected so "today" (expiry, near-expiry contracts, overdue acknowledgments) can be tested at a fixed instant. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
