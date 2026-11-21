package com.terp.erp.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/** Accept-Language: vi | en | ko; anything else falls back to Vietnamese. */
@Configuration
public class I18nConfig {
    @Bean
    public LocaleResolver localeResolver() {
        Locale vi = Locale.forLanguageTag("vi");
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(Nulls.nonNull(List.of(vi, Locale.ENGLISH, Locale.KOREAN)));
        resolver.setDefaultLocale(vi);
        return resolver;
    }
}
