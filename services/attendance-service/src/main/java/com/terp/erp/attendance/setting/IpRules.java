package com.terp.erp.attendance.setting;

import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.exception.ApiException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Allowed-IP entries of the attendance IP restriction: single IPv4/IPv6 addresses or CIDR ranges ("10.0.0.0/24").
 * Only literals are accepted — a host name would make InetAddress do a DNS lookup.
 */
public final class IpRules {
    public static final int MAX_ENTRIES = 100;
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    private IpRules() {
    }

    /** Normalised entries; ATT_057 on the first invalid one, ATT_058 beyond MAX_ENTRIES. */
    public static List<String> validate(List<String> entries) {
        if (entries == null) {
            return List.of();
        }
        List<String> out = entries.stream().filter(e -> e != null && !e.isBlank()).map(String::trim).distinct().toList();
        if (out.size() > MAX_ENTRIES) {
            throw ApiException.badRequest(EAttendanceError.ATT_058, MAX_ENTRIES);
        }
        for (String e : out) {
            if (parse(e) == null) {
                throw ApiException.badRequest(EAttendanceError.ATT_057, e);
            }
        }
        return out;
    }

    /** True when ip falls in one of the entries; an unparsable ip or entry never matches. */
    public static boolean allowed(String ip, List<String> entries) {
        byte[] addr = literal(ip);
        if (addr == null) {
            return false;
        }
        for (String e : entries) {
            Rule r = parse(e);
            if (r != null && r.addr.length == addr.length && matches(r, addr)) {
                return true;
            }
        }
        return false;
    }

    private record Rule(byte[] addr, int prefix) {
    }

    private static Rule parse(String entry) {
        String s = entry.trim();
        int slash = s.indexOf('/');
        String host = slash < 0 ? s : s.substring(0, slash);
        byte[] addr = literal(host);
        if (addr == null) {
            return null;
        }
        int max = addr.length * 8;
        int prefix = max;
        if (slash >= 0) {
            String p = s.substring(slash + 1);
            if (!p.matches("\\d{1,3}")) {
                return null;
            }
            prefix = Integer.parseInt(p);
            if (prefix > max) {
                return null;
            }
        }
        return new Rule(addr, prefix);
    }

    private static boolean matches(Rule r, byte[] addr) {
        int full = r.prefix / 8;
        for (int i = 0; i < full; i++) {
            if (r.addr[i] != addr[i]) {
                return false;
            }
        }
        int rest = r.prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xFF << (8 - rest)) & 0xFF;
        return (r.addr[full] & mask) == (addr[full] & mask);
    }

    /** Bytes of an IP literal (4 or 16), null for anything else. */
    static byte[] literal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        if (IPV4.matcher(v).matches()) {
            for (String part : v.split("\\.")) {
                if (Integer.parseInt(part) > 255) {
                    return null;
                }
            }
        } else if (!(v.contains(":") && IPV6.matcher(v).matches())) {
            return null;
        }
        try {
            // A literal: no lookup happens. "::ffff:1.2.3.4" comes back as the IPv4 address.
            return InetAddress.getByName(v).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
