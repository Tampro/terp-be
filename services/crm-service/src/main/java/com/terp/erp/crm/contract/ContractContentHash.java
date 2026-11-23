package com.terp.erp.crm.contract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 (lower-case hex) of what a signer accepted. Canonical form v1, UTF-8, one line per field in this order:
 * {@code <key>:<length in chars>:<value>\n} for version (=v1), number, subject, customerId, value, startDate,
 * endDate, description. value is the plain decimal without trailing zeros (12000.0000 -> 12000), dates ISO-8601,
 * a missing value is the empty string. The length prefix keeps a newline inside one field from forging another.
 * Never change v1: add a v2 and keep verifying old rows with v1.
 * TODO(assumption): customer name, contract type, status and renewals are not part of the signed content.
 */
final class ContractContentHash {
    static final String VERSION = "v1";

    private ContractContentHash() {
    }

    static String canonical(Contract c) {
        StringBuilder sb = new StringBuilder();
        field(sb, "version", VERSION);
        field(sb, "number", c.getNumber());
        field(sb, "subject", c.getSubject());
        field(sb, "customerId", c.getCustomerId() == null ? null : c.getCustomerId().toString());
        field(sb, "value", plain(c.getValue()));
        field(sb, "startDate", c.getStartDate() == null ? null : c.getStartDate().toString());
        field(sb, "endDate", c.getEndDate() == null ? null : c.getEndDate().toString());
        field(sb, "description", c.getDescription());
        return sb.toString();
    }

    static String sha256(Contract c) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(c).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static void field(StringBuilder sb, String key, String value) {
        String v = value == null ? "" : value;
        sb.append(key).append(':').append(v.length()).append(':').append(v).append('\n');
    }
}
