package com.terp.erp.iam.ip;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * An IPv4 / IPv6 address or CIDR range. Only literal addresses are parsed (the characters are checked first, so
 * InetAddress never does a DNS lookup); the stored form is normalized ("203.0.113.0/24", "2001:db8:0:0:0:0:0:0/32").
 */
public record IpRange(byte[] network, int prefix) {
    private static final Pattern LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /** Parses "1.2.3.4", "1.2.3.0/24", "2001:db8::/32"; IAM_020 otherwise. */
    public static IpRange parse(String text) {
        String t = text == null ? "" : text.trim();
        String addr = t;
        Integer prefix = null;
        int slash = t.indexOf('/');
        if (slash >= 0) {
            addr = t.substring(0, slash);
            try {
                prefix = Integer.parseInt(t.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw bad(t);
            }
        }
        byte[] bytes = literal(addr);
        if (bytes == null) {
            throw bad(t);
        }
        int max = bytes.length * 8;
        int p = prefix == null ? max : prefix;
        if (p < 0 || p > max) {
            throw bad(t);
        }
        return new IpRange(mask(bytes, p), p);
    }

    /** A client address (from the gateway); null when it is not a literal IP. */
    public static byte[] literal(String addr) {
        if (addr == null || !LITERAL.matcher(addr).matches() || (!addr.contains(".") && !addr.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(addr).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public boolean contains(byte[] address) {
        if (address == null || address.length != network.length) {
            return false;
        }
        return java.util.Arrays.equals(mask(address, prefix), network);
    }

    /** Normalized text, kept in the database. */
    public String text() {
        try {
            String host = InetAddress.getByAddress(network).getHostAddress();
            return prefix == network.length * 8 ? host : host + "/" + prefix;
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] mask(byte[] address, int prefix) {
        byte[] out = address.clone();
        for (int i = 0; i < out.length; i++) {
            int bits = Math.max(0, Math.min(8, prefix - i * 8));
            out[i] = (byte) (out[i] & (bits == 0 ? 0 : (0xFF << (8 - bits)) & 0xFF));
        }
        return out;
    }

    private static ApiException bad(String t) {
        return ApiException.badRequest(EIamError.IAM_020, t);
    }
}
