package com.terp.erp.iam.email;

import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {placeholder} with values. Values are data, never markup: in the HTML body they are HTML-escaped (a company
 * named "&lt;script&gt;" stays text); in the subject and sender name line breaks are removed (no header injection).
 * Unknown placeholders are left as written so a typo is visible in the preview.
 */
public final class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    private TemplateRenderer() {
    }

    public static String html(String template, Map<String, String> values) {
        return replace(template, values, true);
    }

    public static String line(String template, Map<String, String> values) {
        return replace(template, values, false).replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String replace(String template, Map<String, String> values, boolean escape) {
        if (template == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            String replacement = v == null ? m.group() : (escape ? HtmlUtils.htmlEscape(v) : v);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
