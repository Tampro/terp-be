package com.terp.erp.iam.integration;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.ESettingsError;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DNS is faked: the tests never touch the network. */
class UrlGuardTest {
    private static InetAddress ip(String s) {
        try {
            return InetAddress.getByName(s);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Map<String, String> DNS = Map.of(
            "hooks.example.com", "93.184.216.34",
            "hooks.slack.com", "54.230.10.10",
            "rebind.example.com", "10.0.0.5",
            "meta.example.com", "169.254.169.254");

    private final UrlGuard guard = new UrlGuard(host -> {
        String a = DNS.get(host);
        if (a == null) {
            throw new UnknownHostException(host);
        }
        return new InetAddress[]{ip(a)};
    });

    private void refused(String url, String allowedHost) {
        assertThatThrownBy(() -> guard.check(url, allowedHost)).as(url).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
    }

    @Test
    void refusesInternalTargets() {
        refused("https://localhost/hook", null);
        refused("https://127.0.0.1/hook", null);
        refused("https://169.254.169.254/latest/meta-data", null);
        refused("https://10.1.2.3/hook", null);
        refused("https://172.16.0.1/hook", null);
        refused("https://192.168.1.10/hook", null);
        refused("https://[::1]/hook", null);
        refused("https://[fd00::1]/hook", null);
        refused("https://[::ffff:127.0.0.1]/hook", null);
        refused("https://0.0.0.0/hook", null);
        refused("https://100.64.0.1/hook", null);
        refused("https://224.0.0.1/hook", null);
        refused("https://keycloak/realms", null);
        refused("https://iam-service.internal/x", null);
        refused("https://rebind.example.com/hook", null);
        refused("https://meta.example.com/hook", null);
        refused("https://unknown.example.com/hook", null);
    }

    @Test
    void refusesOtherSchemesPortsAndCredentials() {
        refused("http://hooks.example.com/hook", null);
        refused("ftp://hooks.example.com/hook", null);
        refused("file:///etc/passwd", null);
        refused("https://user:pass@hooks.example.com/hook", null);
        refused("https://hooks.example.com:22/hook", null);
        refused("not a url", null);
    }

    @Test
    void slackTestIsRestrictedToTheSlackHost() {
        refused("https://hooks.example.com/services/x", CompanyIntegrationService.SLACK_HOST);
        assertThat(guard.check("https://hooks.slack.com/services/T0/B0/X", CompanyIntegrationService.SLACK_HOST)
                .getHost()).isEqualTo("hooks.slack.com");
    }

    @Test
    void acceptsAPublicHttpsHost() {
        assertThat(guard.check("https://hooks.example.com/erp?x=1", null).toString())
                .isEqualTo("https://hooks.example.com/erp?x=1");
        assertThat(guard.check("https://hooks.example.com:8443/erp", null).getPort()).isEqualTo(8443);
    }
}
