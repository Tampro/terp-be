package com.terp.erp.iam.me;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.me.ProfileService.ProfileForm;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private UserAccountRepository users;
    private KeycloakClient keycloak;
    private ProfileService service;
    private UserAccount me;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        keycloak = mock(KeycloakClient.class);
        service = new ProfileService(users, keycloak, mock(AvatarMediaClient.class));
        me = new UserAccount();
        me.setId(7L);
        me.setCompanyId(10L);
        me.setKeycloakId("kc-7");
        me.setUsername("test.seven");
        me.setFullName("Test Seven");
        me.setEmail("seven@example.com");
        when(users.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(me));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mobileIsSavedTrimmedAndReturned() {
        login(new ErpPrincipal(7L, "kc-7", "test.seven", 10L, false, Set.of(), Set.of()));

        var dto = service.update(new ProfileForm("Test Seven", "seven@example.com", " +84 90 000 0000 ", null));

        assertThat(me.getMobile()).isEqualTo("+84 90 000 0000");
        assertThat(dto.mobile()).isEqualTo("+84 90 000 0000");
        assertThat(service.get().mobile()).isEqualTo("+84 90 000 0000");
    }

    @Test
    void blankOrMissingMobileClearsIt() {
        login(new ErpPrincipal(7L, "kc-7", "test.seven", 10L, false, Set.of(), Set.of()));
        me.setMobile("+84 90 000 0000");

        service.update(new ProfileForm("Test Seven", "seven@example.com", "  ", null));
        assertThat(me.getMobile()).isNull();

        me.setMobile("+84 90 000 0000");
        service.update(new ProfileForm("Test Seven", "seven@example.com", null, null));
        assertThat(me.getMobile()).isNull();
    }

    @Test
    void superAdminCannotSetAMobileNumber() {
        login(new ErpPrincipal(null, "kc-root", "superadmin", null, true, Set.of(ErpPrincipal.SUPER_ADMIN_ROLE),
                Set.of()));

        assertThatThrownBy(() -> service.update(new ProfileForm("Root", "root@example.com", "+84 90 000 0000", null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_031);
        verify(keycloak, never()).updateProfile(anyString(), anyString(), anyString());

        var dto = service.update(new ProfileForm("Root", "root@example.com", null, null));
        assertThat(dto.mobile()).isNull();
        assertThat(dto.superAdmin()).isTrue();
    }

    @Test
    void mobileAcceptsDigitsAndSeparatorsOnly() {
        assertThat(VALIDATOR.validate(new ProfileForm("A", "a@example.com", "+84 (90) 000-00.00", null))).isEmpty();
        assertThat(VALIDATOR.validate(new ProfileForm("A", "a@example.com", "", null))).isEmpty();
        assertThat(VALIDATOR.validate(new ProfileForm("A", "a@example.com", "call me", null)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("mobile");
        assertThat(VALIDATOR.validate(new ProfileForm("A", "a@example.com", "1".repeat(31), null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("mobile");
    }

    private static void login(ErpPrincipal p) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
