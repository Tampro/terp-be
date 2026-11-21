package com.terp.erp.employee.announcement;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * The rich-text content is shown to every employee of the audience: only formatting tags survive (no script, style,
 * event handler, iframe or javascript: link), so one admin's HTML cannot run in another user's browser.
 */
final class HtmlSanitizer {
    private static final Safelist SAFE = Safelist.relaxed()
            .addTags("span", "s", "hr")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
            .preserveRelativeLinks(false);
    private static final Document.OutputSettings OUT = new Document.OutputSettings().prettyPrint(false);

    private HtmlSanitizer() {
    }

    static String clean(String html) {
        return Jsoup.clean(html == null ? "" : html, "", SAFE, OUT).trim();
    }

    /** Plain text of the content, cut for card previews. */
    static String preview(String html, int max) {
        String text = Jsoup.parse(html == null ? "" : html).text().trim();
        return text.length() <= max ? text : text.substring(0, max).trim() + "…";
    }
}
