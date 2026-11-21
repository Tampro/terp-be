package com.terp.erp.iam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async listeners (EmailDispatcher) run on Spring Boot's task executor, off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
