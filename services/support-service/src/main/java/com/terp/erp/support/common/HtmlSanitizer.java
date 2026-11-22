package com.terp.erp.support.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Rich text (ticket description and replies, knowledge base content, FAQ answers, custom pages) is shown to staff and
 * on the public portal: only formatting tags survive (no script, style, event handler, iframe or javascript: link), so
 * no submitted HTML can run in another user's browser. Same rules as recruitment-service and employee-service.
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

    /** True when the HTML holds no visible text (e.g. "<p></p>" or only a script that clean() removes). */
    public static boolean isBlank(String html) {
        return Jsoup.parse(clean(html)).text().isBlank();
    }
}
