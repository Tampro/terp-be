package com.terp.erp.iam.settings;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;

import java.time.Duration;

/**
 * Checks a reCAPTCHA token with Google when the super admin enabled it. Fails closed: no token, a refused token
 * or Google unreachable all stop the login. v3 tokens must also reach the score threshold.
 */
@Slf4j
@Component
public class RecaptchaVerifier {
    private static final double V3_MIN_SCORE = 0.5;

    private final SettingsService settings;
    private final RestClient http;
    private final String verifyUrl;

    public RecaptchaVerifier(SettingsService settings,
                             @Value("${erp.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl) {
        this.settings = settings;
        this.verifyUrl = verifyUrl;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /** No-op when reCAPTCHA is disabled. */
    public void check(String token) {
        SettingsService.ActiveRecaptcha active = settings.activeRecaptcha();
        if (active == null) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest(EIamError.AUTH_010);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", active.secret());
        form.add("response", token);
        JsonNode body;
        try {
            body = http.post().uri(verifyUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("reCAPTCHA verification unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_011);
        }
        boolean ok = body != null && body.path("success").asBoolean(false);
        if (ok && active.version() == SettingsDtos.ERecaptchaVersion.V3) {
            ok = body.path("score").asDouble(0) >= V3_MIN_SCORE;
        }
        if (!ok) {
            throw ApiException.badRequest(EIamError.AUTH_010);
        }
    }
}
