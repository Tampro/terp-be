package com.terp.erp.net;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedHttpTest {
    private static InetAddress ip(String s) throws UnknownHostException {
        return InetAddress.getByName(s);
    }

    @Test
    void resolverRefusesAHostWithAnyNonPublicAddress() throws Exception {
        var r = new PinnedHttp.PublicOnlyResolver(h -> new InetAddress[]{ip("93.184.216.34"), ip("10.0.0.5")});
        assertThatThrownBy(() -> r.resolve("hooks.example.com")).isInstanceOf(UnknownHostException.class);
        var meta = new PinnedHttp.PublicOnlyResolver(h -> new InetAddress[]{ip("169.254.169.254")});
        assertThatThrownBy(() -> meta.resolve("hooks.example.com")).isInstanceOf(UnknownHostException.class);
        var ok = new PinnedHttp.PublicOnlyResolver(h -> new InetAddress[]{ip("93.184.216.34")});
        assertThat(ok.resolve("hooks.example.com")).containsExactly(ip("93.184.216.34"));
    }

    @Test
    void rebindingToLoopbackAtConnectTimeIsRefused() {
        // First lookup (UrlGuard.check) would see a public address; the one the client connects with sees loopback.
        AtomicInteger lookups = new AtomicInteger();
        UrlGuard.HostResolver rebinding = h -> new InetAddress[]{
                lookups.getAndIncrement() == 0 ? ip("93.184.216.34") : ip("127.0.0.1")};
        new UrlGuard(rebinding).check("https://hooks.example.com/x", null);

        PinnedHttp http = new PinnedHttp(Duration.ofSeconds(1), Duration.ofSeconds(1), "test", rebinding);
        PinnedHttp.Exchange x = http.execute(new HttpPost("https://hooks.example.com/x"), 0);

        assertThat(x.status()).isNull();
        assertThat(x.failure()).isEqualTo("CONNECTION_ERROR");
        assertThat(lookups.get()).isEqualTo(2);
    }
}
