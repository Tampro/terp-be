package com.terp.erp.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Proxies GET /docs/{service}/v3/api-docs to that service's own spec so one Swagger UI on the gateway lists them all.
 * Host is preserved so springdoc writes the gateway as the spec's server and "Try it out" goes through the gateway.
 */
@Configuration
@ConditionalOnProperty(name = "erp.swagger.enabled", havingValue = "true")
public class SwaggerDocsRoutes {

    /** Docs path name, URL env var (same one the API route uses), local default port. */
    private record Docs(String name, String urlVar, int port) {
    }

    private static final List<Docs> SERVICES = List.of(
            new Docs("iam", "IAM_SERVICE_URL", 8081),
            new Docs("media", "MEDIA_SERVICE_URL", 8082),
            new Docs("employee", "EMPLOYEE_SERVICE_URL", 8083),
            new Docs("attendance", "ATTENDANCE_SERVICE_URL", 8084),
            new Docs("recruitment", "RECRUITMENT_SERVICE_URL", 8085),
            new Docs("payroll", "PAYROLL_SERVICE_URL", 8086),
            new Docs("asset", "ASSET_SERVICE_URL", 8087),
            new Docs("meeting", "MEETING_SERVICE_URL", 8088),
            new Docs("document", "DOCUMENT_SERVICE_URL", 8089),
            new Docs("report", "REPORT_SERVICE_URL", 8090),
            new Docs("inventory", "INVENTORY_SERVICE_URL", 8091),
            new Docs("commerce", "COMMERCE_SERVICE_URL", 8092),
            new Docs("accounting", "ACCOUNTING_SERVICE_URL", 8093),
            new Docs("crm", "CRM_SERVICE_URL", 8094),
            new Docs("project", "PROJECT_SERVICE_URL", 8095),
            new Docs("support", "SUPPORT_SERVICE_URL", 8096),
            new Docs("notification", "NOTIFICATION_SERVICE_URL", 8097));

    @Bean
    RouteLocator swaggerDocsRouteLocator(RouteLocatorBuilder builder, Environment env) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (Docs d : SERVICES) {
            String uri = env.getProperty(d.urlVar(), "http://localhost:" + d.port());
            routes.route("docs-" + d.name(), r -> r
                    .method(HttpMethod.GET).and().path("/docs/" + d.name() + "/v3/api-docs")
                    .filters(f -> f.preserveHostHeader().setPath("/v3/api-docs"))
                    .uri(uri));
        }
        return routes.build();
    }
}
