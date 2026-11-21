package com.terp.erp.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * Proxies GET /docs/{service}/v3/api-docs to that service's own spec so one Swagger UI on the gateway lists them all.
 * Host is preserved so springdoc writes the gateway as the spec's server and "Try it out" goes through the gateway.
 */
@Configuration
@ConditionalOnProperty(name = "erp.swagger.enabled", havingValue = "true")
public class SwaggerDocsRoutes {

    @Bean
    RouteLocator swaggerDocsRouteLocator(RouteLocatorBuilder builder,
            @Value("${IAM_SERVICE_URL:http://localhost:8081}") String iam,
            @Value("${MEDIA_SERVICE_URL:http://localhost:8082}") String media,
            @Value("${EMPLOYEE_SERVICE_URL:http://localhost:8083}") String employee,
            @Value("${ATTENDANCE_SERVICE_URL:http://localhost:8084}") String attendance,
            @Value("${RECRUITMENT_SERVICE_URL:http://localhost:8085}") String recruitment,
            @Value("${PAYROLL_SERVICE_URL:http://localhost:8086}") String payroll,
            @Value("${ASSET_SERVICE_URL:http://localhost:8087}") String asset,
            @Value("${MEETING_SERVICE_URL:http://localhost:8088}") String meeting,
            @Value("${DOCUMENT_SERVICE_URL:http://localhost:8089}") String document,
            @Value("${REPORT_SERVICE_URL:http://localhost:8090}") String report) {
        Map<String, String> services = Map.of("iam", iam, "media", media, "employee", employee,
                "attendance", attendance, "recruitment", recruitment, "payroll", payroll, "asset", asset,
                "meeting", meeting, "document", document, "report", report);
        RouteLocatorBuilder.Builder routes = builder.routes();
        services.forEach((name, uri) -> routes.route("docs-" + name, r -> r
                .method(HttpMethod.GET).and().path("/docs/" + name + "/v3/api-docs")
                .filters(f -> f.preserveHostHeader().setPath("/v3/api-docs"))
                .uri(uri)));
        return routes.build();
    }
}
