package com.terp.erp.jobs;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Scheduled jobs annotated with {@code @SchedulerLock} run on one instance at a time: the lock is a row of the
 * service's own {@code shedlock} table (Liquibase), timed by the database clock so instance clocks don't matter.
 * Opt-in per service with {@code erp.jobs.lock.enabled=true} plus the shedlock dependencies (optional in erp-common)
 * and the table. A job that dies keeps its lock until its {@code lockAtMostFor}.
 */
@Configuration
@ConditionalOnProperty(name = "erp.jobs.lock.enabled", havingValue = "true")
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class JobLockConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
