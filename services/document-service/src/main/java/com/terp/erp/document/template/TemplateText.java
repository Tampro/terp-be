package com.terp.erp.document.template;

import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {{variable_name}} placeholders of a template: listing, filling, and the counts of the list (variables, clauses).
 * Values are inserted as plain text (the output is a PDF, not HTML, so no markup is interpreted).
 */
public final class TemplateText {
    static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]{0,59})\\s*}}");
    /**
     * A clause starts a line with a number ("1.", "2)", "3.1") or a heading word.
     * TODO(assumption): the spec does not define a clause; confirm the rule with BA.
     */
    private static final Pattern CLAUSE = Pattern.compile("(?imu)^\\s*(\\d+(\\.\\d+)*[.)]\\s+\\S|(clause|article|section|điều)\\s+\\d)");

    private TemplateText() {
    }

    /** The declared names (comma list) followed by the content's placeholders, without duplicates. */
    public static List<String> variables(String declared, String content) {
        Set<String> out = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        if (declared != null) {
            for (String raw : declared.split(",")) {
                String v = raw.trim();
                if (v.isEmpty()) {
                    continue;
                }
                if (!NAME.matcher(v).matches()) {
                    bad.add(v);
                }
                out.add(v);
            }
        }
        if (!bad.isEmpty()) {
            throw ApiException.badRequest(EDocumentError.DOC_022, String.join(", ", bad));
        }
        Matcher m = PLACEHOLDER.matcher(content == null ? "" : content);
        while (m.find()) {
            out.add(m.group(1));
        }
        return List.copyOf(out);
    }

    public static int clauses(String content) {
        Matcher m = CLAUSE.matcher(content == null ? "" : content);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Replaces every placeholder; a missing value is reported (DOC_021) unless sample mode shows [name]. */
    public static String fill(String content, Map<String, String> values, boolean sample) {
        Matcher m = PLACEHOLDER.matcher(content);
        StringBuilder out = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null || v.isBlank()) {
                if (!sample) {
                    missing.add(m.group(1));
                }
                v = "[" + m.group(1) + "]";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(EDocumentError.DOC_021, String.join(", ", missing));
        }
        return out.toString();
    }
}
