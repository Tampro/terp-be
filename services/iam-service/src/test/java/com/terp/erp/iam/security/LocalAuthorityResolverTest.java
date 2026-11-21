package com.terp.erp.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAuthorityResolverTest {

    @Test
    void tokenOfALoggedOutSessionIsRejectedEvenIfItsPrincipalWasCached() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        when(users.findByKeycloakIdAndDeletedFalse(anyString())).thenReturn(Optional.empty());
        RevokedSessionStore revoked = new RevokedSessionStore(Duration.ofMinutes(15));
        LocalAuthorityResolver resolver = new LocalAuthorityResolver(users, mock(CompanyRepository.class), revoked,
                mock(com.terp.erp.iam.saas.SubscriptionService.class),
                mock(com.terp.erp.iam.impersonation.ImpersonationService.class));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(900), Map.of("alg", "RS256"),
                Map.of("sub", "kc-root", "sid", "sid-1", "realm_access", Map.of("roles", List.of("SUPER_ADMIN"))));

        assertThat(resolver.resolve(jwt).superAdmin()).isTrue(); // cached now

        revoked.revoke("sid-1");
        assertThatThrownBy(() -> resolver.resolve(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }
}
