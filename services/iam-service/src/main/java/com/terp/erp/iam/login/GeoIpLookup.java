package com.terp.erp.iam.login;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CityResponse;
import com.terp.erp.net.UrlGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * City / country / "ISP" of a login IP from local GeoLite2 files — offline, nothing is sent to MaxMind or anyone.
 * GeoLite2 has no free ISP database: the ASN database's autonomous-system organization is the closest free source and
 * is stored as the ISP. Private, loopback, link-local, unique-local and CGNAT addresses are not looked up. Logs never
 * carry the IP. DatabaseReader is thread-safe, so one instance serves all requests.
 */
@Slf4j
@Component
public class GeoIpLookup implements DisposableBean {
    public record Geo(String city, String country, String isp) {
    }

    /** City database view (an interface so tests need no real .mmdb). */
    interface CitySource {
        Optional<Geo> city(InetAddress ip) throws Exception;
    }

    /** ASN database view: the autonomous-system organization. */
    interface AsnSource {
        Optional<String> organization(InetAddress ip) throws Exception;
    }

    // IP literals only, so InetAddress.getByName never resolves a name through DNS.
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final CitySource citySource;
    private final AsnSource asnSource;
    private final List<Closeable> readers = new ArrayList<>();

    @Autowired
    public GeoIpLookup(GeoIpProperties props) {
        DatabaseReader city = open("city", props == null ? null : props.cityDb());
        DatabaseReader asn = open("ASN", props == null ? null : props.asnDb());
        if (city != null) {
            readers.add(city);
        }
        if (asn != null) {
            readers.add(asn);
        }
        this.citySource = city == null ? null : ip -> city.tryCity(ip).map(GeoIpLookup::fromCity);
        this.asnSource = asn == null ? null : ip -> asn.tryAsn(ip).map(r -> r.getAutonomousSystemOrganization());
    }

    GeoIpLookup(CitySource citySource, AsnSource asnSource) {
        this.citySource = citySource;
        this.asnSource = asnSource;
    }

    public boolean enabled() {
        return citySource != null || asnSource != null;
    }

    public Optional<Geo> lookup(String ip) {
        if (!enabled() || ip == null) {
            return Optional.empty();
        }
        String literal = ip.trim();
        if (!IP_LITERAL.matcher(literal).matches() || (literal.indexOf('.') < 0 && literal.indexOf(':') < 0)) {
            return Optional.empty();
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(literal);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!UrlGuard.isPublic(address)) {
            return Optional.empty();
        }
        Geo place = null;
        String isp = null;
        try {
            place = citySource == null ? null : citySource.city(address).orElse(null);
        } catch (AddressNotFoundException e) {
            // not in the database
        } catch (Exception e) {
            log.debug("GeoIP city lookup failed: {}", e.getClass().getSimpleName());
        }
        try {
            isp = asnSource == null ? null : asnSource.organization(address).orElse(null);
        } catch (AddressNotFoundException e) {
            // not in the database
        } catch (Exception e) {
            log.debug("GeoIP ASN lookup failed: {}", e.getClass().getSimpleName());
        }
        String city = cut(place == null ? null : place.city(), 100);
        String country = cut(place == null ? null : place.country(), 100);
        isp = cut(isp, 150); // login_history.isp is VARCHAR(150)
        return city == null && country == null && isp == null ? Optional.empty()
                : Optional.of(new Geo(city, country, isp));
    }

    // TODO(assumption): English names (GeoLite2 also has vi for many places), and the country stored as its English
    // name rather than the ISO code (ISO code only when no name) — confirm with BA.
    private static Geo fromCity(CityResponse r) {
        String country = r.getCountry() == null ? null : r.getCountry().getName();
        if (country == null && r.getCountry() != null) {
            country = r.getCountry().getIsoCode();
        }
        return new Geo(r.getCity() == null ? null : r.getCity().getName(), country, null);
    }

    private static String cut(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static DatabaseReader open(String kind, String path) {
        if (path == null || path.isBlank()) {
            log.info("GeoIP {} database not configured: lookup off", kind);
            return null;
        }
        File file = new File(path.trim());
        if (!file.isFile() || !file.canRead()) {
            log.warn("GeoIP {} database {} is missing or unreadable: lookup off", kind, file.getPath());
            return null;
        }
        try {
            DatabaseReader reader = new DatabaseReader.Builder(file).withCache(new CHMCache()).build();
            log.info("GeoIP {} database loaded: {} ({})", kind, file.getPath(),
                    reader.getMetadata().getDatabaseType());
            return reader;
        } catch (IOException | RuntimeException e) {
            log.warn("GeoIP {} database {} could not be opened ({}): lookup off", kind, file.getPath(),
                    e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public void destroy() {
        for (Closeable c : readers) {
            try {
                c.close();
            } catch (IOException e) {
                log.debug("GeoIP reader close failed: {}", e.getClass().getSimpleName());
            }
        }
    }
}
