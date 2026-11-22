package com.terp.erp.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Opt-in transactional outbox: active only with erp.outbox.enabled=true and both spring-boot-starter-data-jpa and
 * spring-boot-starter-amqp on the classpath. Services keep their @SpringBootApplication in com.terp.erp.&lt;svc&gt;, so
 * JPA would scan only that package for entities and repositories: this adds com.terp.erp.outbox to the
 * auto-configuration packages (additive, unlike @EntityScan which would replace the service's own package). It must
 * therefore be processed before the JPA auto-configurations.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.core.RabbitTemplate", "jakarta.persistence.EntityManager",
        "org.springframework.data.jpa.repository.JpaRepository"})
@ConditionalOnProperty(prefix = "erp.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
@Import(OutboxAutoConfiguration.OutboxPackage.class)
public class OutboxAutoConfiguration {

    static class OutboxPackage implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata,
                                            @NonNull BeanDefinitionRegistry registry) {
            AutoConfigurationPackages.register(registry, OutboxEvent.class.getPackageName());
        }
    }

    /** Declared by every producer (idempotent on the broker), so publishing never depends on a consumer being up. */
    @Bean
    TopicExchange erpEventsExchange(OutboxProperties props) {
        return new TopicExchange(props.exchange(), true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(OutboxRepository repo, ObjectMapper mapper) {
        return new OutboxPublisher(repo, mapper, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxSender outboxSender(RabbitTemplate template, ObjectMapper mapper, OutboxProperties props) {
        return new RabbitOutboxSender(template, mapper, props);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRelay outboxRelay(OutboxRepository repo, OutboxSender sender, OutboxProperties props,
                            PlatformTransactionManager tm) {
        return new OutboxRelay(repo, sender, props, new TransactionTemplate(com.terp.erp.common.Nulls.nonNull(tm)), Clock.systemUTC());
    }
}
