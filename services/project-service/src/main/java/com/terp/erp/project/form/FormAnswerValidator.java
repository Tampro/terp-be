package com.terp.erp.project.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EFormError;
import com.terp.erp.project.form.FormField.EType;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side checks of a public submission: the browser's checks are a convenience, this is the rule. Errors name the
 * field label, never the submitted value (it may be personal data).
 */
final class FormAnswerValidator {
    // TODO(assumption): limits not given by BA; chosen to fit the columns and keep a public POST small.
    static final int MAX_FIELDS = 50;
    static final int MAX_OPTIONS = 100;
    static final int MAX_TEXT = 255;
    static final int MAX_TEXTAREA = 5000;
    static final int MAX_URL = 2048;
    static final int MAX_PHONE = 30;
    static final long MAX_BODY_BYTES = 64 * 1024;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // TODO(assumption): international format, 6-15 digits with optional +, spaces, dots, dashes, parentheses.
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ().-]{6,30}$");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What is stored for one field. value: String, List of String (CHECKBOX) or null when left empty. */
    record Answer(String key, String label, EType type, Object value) {
    }

    private FormAnswerValidator() {
    }

    static List<Answer> validate(List<FormField> fields, Map<String, JsonNode> answers) {
        Map<String, JsonNode> given = answers == null ? Map.of() : answers;
        if (given.size() > fields.size()) {
            throw ApiException.badRequest(EFormError.FRM_019);
        }
        Set<String> keys = new java.util.HashSet<>();
        fields.forEach(f -> keys.add(f.getFieldKey()));
        for (String k : given.keySet()) {
            if (!keys.contains(k)) {
                throw ApiException.badRequest(EFormError.FRM_013, k.length() > 40 ? k.substring(0, 40) : k);
            }
        }
        List<Answer> out = new ArrayList<>(fields.size());
        for (FormField f : fields) {
            out.add(new Answer(f.getFieldKey(), f.getLabel(), f.getType(), check(f, given.get(f.getFieldKey()))));
        }
        return out;
    }

    private static Object check(FormField f, JsonNode node) {
        if (f.getType() == EType.CHECKBOX) {
            List<String> picked = new ArrayList<>();
            if (node != null && node.isArray()) {
                node.forEach(n -> picked.add(scalar(f, n)));
            } else if (node != null && !node.isNull()) {
                picked.add(scalar(f, node));
            }
            picked.removeIf(String::isBlank);
            if (picked.isEmpty()) {
                if (f.isRequired()) {
                    throw ApiException.badRequest(EFormError.FRM_004, f.getLabel());
                }
                return null;
            }
            List<String> options = FormService.readOptions(f.getOptions());
            if (picked.size() > options.size() || !options.containsAll(picked)) {
                throw ApiException.badRequest(EFormError.FRM_011, f.getLabel());
            }
            return List.copyOf(new java.util.LinkedHashSet<>(picked));
        }
        String v = node == null || node.isNull() ? "" : scalar(f, node).trim();
        if (v.isEmpty()) {
            if (f.isRequired()) {
                throw ApiException.badRequest(EFormError.FRM_004, f.getLabel());
            }
            return null;
        }
        int max = switch (f.getType()) {
            case TEXTAREA -> MAX_TEXTAREA;
            case URL -> MAX_URL;
            case PHONE -> MAX_PHONE;
            default -> MAX_TEXT;
        };
        if (v.length() > max) {
            throw ApiException.badRequest(EFormError.FRM_012, f.getLabel(), max);
        }
        switch (f.getType()) {
            case EMAIL -> require(EMAIL.matcher(v).matches(), EFormError.FRM_005, f);
            case NUMBER -> require(isNumber(v), EFormError.FRM_006, f);
            case PHONE -> require(PHONE.matcher(v).matches() && digits(v) >= 6 && digits(v) <= 15,
                    EFormError.FRM_007, f);
            case URL -> require(isHttpUrl(v), EFormError.FRM_008, f);
            case DATE -> require(parses(() -> LocalDate.parse(v)), EFormError.FRM_009, f);
            case TIME -> require(parses(() -> LocalTime.parse(v, HH_MM)), EFormError.FRM_010, f);
            case SELECT, RADIO -> require(FormService.readOptions(f.getOptions()).contains(v), EFormError.FRM_011, f);
            // TODO(assumption): a PASSWORD answer is never kept readable: salted SHA-256 (salt$hash). It proves a
            // value was given and can be compared later; SHA-256 is fast, so a weak password could be brute-forced
            // from a stolen database. If BA wants no trace at all, refuse PASSWORD fields instead.
            case PASSWORD -> {
                return hash(v);
            }
            default -> {
                // TEXT, TEXTAREA: length only
            }
        }
        return v;
    }

    private static String scalar(FormField f, JsonNode n) {
        if (n.isTextual() || n.isNumber() || n.isBoolean()) {
            return n.asText();
        }
        throw ApiException.badRequest(EFormError.FRM_018, f.getLabel());
    }

    private static void require(boolean ok, EFormError error, FormField f) {
        if (!ok) {
            throw ApiException.badRequest(error, f.getLabel());
        }
    }

    private static boolean isNumber(String v) {
        try {
            new BigDecimal(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long digits(String v) {
        return v.chars().filter(Character::isDigit).count();
    }

    private static boolean isHttpUrl(String v) {
        try {
            URI u = new URI(v);
            return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean parses(Runnable r) {
        try {
            r.run();
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static String hash(String value) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] h = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256$" + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }
}
