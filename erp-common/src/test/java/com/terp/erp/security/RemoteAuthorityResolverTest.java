package com.terp.erp.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A local HTTP server stands in for IAM; ttl 1 ms so every resolve past the first goes back to "IAM". */
class RemoteAuthorityResolverTest {
    private static final String OK = "{\"data\":{\"userId\":7,\"keycloakId\":\"kc-7\",\"username\":\"test.user\","
            + "\"companyId\":3,\"superAdmin\":false,\"roles\":[\"EMPLOYEE\"],\"permissions\":[\"EMP_VIEW\"]}}";

    private HttpServer iam;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private RemoteAuthorityResolver resolver;

    @BeforeEach
    void start() throws Exception {
        iam = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        iam.createContext("/api/v1/me/authorities", ex -> {
            calls.incrementAndGet();
            byte[] body = (status.get() == 200 ? OK : "{}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status.get(), body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        iam.start();
        resolver = new RemoteAuthorityResolver("http://127.0.0.1:" + iam.getAddress().getPort(),
                Duration.ofMillis(1), Duration.ofMinutes(5));
    }

    @AfterEach
    void stop() {
        iam.stop(0);
    }

    private static Jwt jwt(String token) {
        return Jwt.withTokenValue(token).header("alg", "none").claim("sub", "kc-7").build();
    }

    private static void pastTtl() throws InterruptedException {
        Thread.sleep(5);
    }

    @Test
    void iamDownAnswersFromTheLastGoodAuthorities() throws Exception {
        assertThat(resolver.resolve(jwt("t1")).permissions()).containsExactly("EMP_VIEW");
        iam.stop(0); // connection refused
        pastTtl();
        assertThat(resolver.resolve(jwt("t1")).permissions()).containsExactly("EMP_VIEW");
    }

    @Test
    void iam5xxAnswersFromTheLastGoodAuthorities() throws Exception {
        resolver.resolve(jwt("t1"));
        status.set(503);
        pastTtl();
        assertThat(resolver.resolve(jwt("t1")).userId()).isEqualTo(7L);
    }

    @Test
    void aTokenIamNeverAnsweredForStillFailsClosed() throws Exception {
        resolver.resolve(jwt("t1"));
        iam.stop(0);
        assertThatThrownBy(() -> resolver.resolve(jwt("t2"))).isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void aRefusalFromIamIsFinalEvenWithAStaleCopy() throws Exception {
        resolver.resolve(jwt("t1"));
        status.set(401); // e.g. the account was locked meanwhile
        pastTtl();
        assertThatThrownBy(() -> resolver.resolve(jwt("t1"))).isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void afterAFailureIamIsNotCalledAgainDuringTheBackoff() throws Exception {
        resolver.resolve(jwt("t1"));
        status.set(503);
        pastTtl();
        resolver.resolve(jwt("t1"));
        int afterFailure = calls.get();
        pastTtl();
        resolver.resolve(jwt("t1"));
        resolver.resolve(jwt("t1"));
        assertThat(calls.get()).isEqualTo(afterFailure);
    }

    @Test
    void staleCopyExpiresAfterTheGrace() throws Exception {
        RemoteAuthorityResolver shortGrace = new RemoteAuthorityResolver(
                "http://127.0.0.1:" + iam.getAddress().getPort(), Duration.ofMillis(1), Duration.ofMillis(20));
        shortGrace.resolve(jwt("t1"));
        iam.stop(0);
        Thread.sleep(60);
        assertThatThrownBy(() -> shortGrace.resolve(jwt("t1"))).isInstanceOf(AuthenticationServiceException.class);
    }
}
