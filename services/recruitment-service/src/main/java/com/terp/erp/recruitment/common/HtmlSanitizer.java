package com.terp.erp.recruitment.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Rich text (job description, requirements, benefits) is shown to admins and, once published, to anyone on the career
 * page: only formatting tags survive (no script, style, event handler, iframe or javascript: link), so an admin's HTML
 * cannot run in another user's browser. Same rules as the announcements of employee-service.
 */
public final class HtmlSanitizer {
    private static final Safelist SAFE = Safelist.relaxed()
            .addTags("span", "s", "hr")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
            .preserveRelativeLinks(false);
    private static final Document.OutputSettings OUT = new Document.OutputSettings().prettyPrint(false);

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        return Jsoup.clean(html == null ? "" : html, "", SAFE, OUT).trim();
    }

    /** Plain text of the content, cut for card previews. */
    public static String preview(String html, int max) {
        String text = Jsoup.parse(html == null ? "" : html).text().trim();
        return text.length() <= max ? text : text.substring(0, max).trim() + "…";
    }
}
