package com.terp.erp.notification.config;

import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The SSRF rules and the outbound POST shared with iam-service (erp-common): https, public hosts, no redirects. */
@Configuration
public class NetConfig {
    @Bean
    UrlGuard urlGuard() {
        return new UrlGuard();
    }

    @Bean
    OutboundHttp outboundHttp() {
        return new OutboundHttp();
    }
}
