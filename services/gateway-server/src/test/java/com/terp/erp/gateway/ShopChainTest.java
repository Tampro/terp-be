package com.terp.erp.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** /api/v1/shop/** trusts realm erp-customer only, and its chain is consulted before the staff chain. */
class ShopChainTest {
    private static final String CUSTOMER = "http://localhost:18180/realms/erp-customer";

    @Test
    void customerIssuerOnlyOnTheShopChain() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(CUSTOMER)); // as customerDecoder() does
        assertThat(decoder.decode(sign(keys, CUSTOMER)).block().getSubject()).isEqualTo("cust-1");
        assertThatThrownBy(() -> decoder.decode(sign(keys, "http://localhost:18180/realms/erp")).block())
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shopChainIsOrderedBeforeTheStaffChain() throws NoSuchMethodException {
        int shop = GatewaySecurityConfig.class.getDeclaredMethod("shopFilterChain", ServerHttpSecurity.class,
                String.class, String.class).getAnnotation(Order.class).value();
        int staff = GatewaySecurityConfig.class.getDeclaredMethod("gatewayFilterChain", ServerHttpSecurity.class,
                boolean.class).getAnnotation(Order.class).value();
        assertThat(shop).isLessThan(staff);
        assertThat(GatewaySecurityConfig.SHOP_PATH).isEqualTo("/api/v1/shop/**");
    }

    private static String sign(KeyPair keys, String issuer) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("cust-1").issuer(issuer)
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 300_000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
}
