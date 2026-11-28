package com.terp.erp.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The customer chain trusts realm erp-customer only: a token of realm erp (staff) is rejected on its issuer. */
class ShopResourceServerSecurityTest {
    private static final String CUSTOMER = "http://localhost:18180/realms/erp-customer";
    private static final String STAFF = "http://localhost:18180/realms/erp";

    @Test
    void customerIssuerIsAcceptedStaffIssuerIsRejected() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(CUSTOMER)); // what customerDecoder() sets

        Jwt ok = decoder.decode(sign(keys, CUSTOMER));
        assertThat(ok.getSubject()).isEqualTo("cust-1");
        assertThatThrownBy(() -> decoder.decode(sign(keys, STAFF))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void principalCarriesSubAzpEmailAndNoAuthority() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("cust-1").claim("azp", "erp-shop-web")
                .claim("email", "c@example.com").issuer(CUSTOMER)
                .issuedAt(Instant.parse("2026-11-28T00:00:00Z")).expiresAt(Instant.parse("2026-11-28T00:05:00Z")).build();
        var auth = ShopResourceServerSecurity.toAuthentication(jwt);
        assertThat(auth.getPrincipal()).isEqualTo(new CustomerPrincipal("cust-1", "erp-shop-web", "c@example.com"));
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.isAuthenticated()).isTrue();
    }

    private static String sign(KeyPair keys, String issuer) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("cust-1").issuer(issuer)
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 300_000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
}
