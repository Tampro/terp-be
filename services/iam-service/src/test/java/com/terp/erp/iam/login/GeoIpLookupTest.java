package com.terp.erp.iam.login;

import com.maxmind.geoip2.exception.AddressNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeoIpLookupTest {
    private final GeoIpLookup.CitySource city = mock(GeoIpLookup.CitySource.class);
    private final GeoIpLookup.AsnSource asn = mock(GeoIpLookup.AsnSource.class);
    private final GeoIpLookup lookup = new GeoIpLookup(city, asn);

    @Test
    void privateAndNonLiteralAddressesAreNotLookedUp() {
        for (String ip : new String[]{"10.0.0.1", "172.18.0.5", "192.168.1.2", "127.0.0.1", "169.254.1.1",
                "100.64.0.1", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1", "example.com", "", null}) {
            assertThat(lookup.lookup(ip)).as(String.valueOf(ip)).isEmpty();
        }
        verifyNoInteractions(city, asn);
    }

    @Test
    void foundMapsAndTruncates() throws Exception {
        when(city.city(any(InetAddress.class))).thenReturn(Optional.of(
                new GeoIpLookup.Geo("C".repeat(120), "Testland", null)));
        when(asn.organization(any(InetAddress.class))).thenReturn(Optional.of("I".repeat(200)));

        GeoIpLookup.Geo g = lookup.lookup("8.8.8.8").orElseThrow();

        assertThat(g.city()).hasSize(100);
        assertThat(g.country()).isEqualTo("Testland");
        assertThat(g.isp()).hasSize(150);
    }

    @Test
    void addressNotFoundIsEmpty() throws Exception {
        when(city.city(any(InetAddress.class))).thenThrow(new AddressNotFoundException("not found"));
        when(asn.organization(any(InetAddress.class))).thenReturn(Optional.empty());

        assertThat(lookup.lookup("2001:4860:4860::8888")).isEmpty();
    }

    @Test
    void readerFailureIsEmptyAndOtherDatabaseStillUsed() throws Exception {
        when(city.city(any(InetAddress.class))).thenThrow(new IOException("corrupt"));
        when(asn.organization(any(InetAddress.class))).thenThrow(new IllegalStateException("boom"));
        assertThat(lookup.lookup("8.8.4.4")).isEmpty();

        when(asn.organization(any(InetAddress.class))).thenReturn(Optional.of("Example Net"));
        assertThat(lookup.lookup("8.8.4.4")).contains(new GeoIpLookup.Geo(null, null, "Example Net"));
    }

    @Test
    void missingOrUnconfiguredFilesDisableLookupWithoutFailing(@TempDir Path dir) {
        GeoIpLookup missing = new GeoIpLookup(new GeoIpProperties(dir.resolve("GeoLite2-City.mmdb").toString(),
                dir.toString())); // a directory is not a readable database file
        assertThat(missing.enabled()).isFalse();
        assertThat(missing.lookup("8.8.8.8")).isEmpty();

        GeoIpLookup none = new GeoIpLookup(new GeoIpProperties("", null));
        assertThat(none.enabled()).isFalse();
        none.destroy();
    }
}
