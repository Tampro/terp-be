package com.terp.erp.employee.certificate;

import java.util.Map;

/** Test access to the package-private PDF renderer. */
public final class LetterAccess {
    private LetterAccess() {
    }

    public static byte[] pdf(String[] text, Map<String, String> v) {
        StringBuilder body = new StringBuilder();
        for (int i = 2; i < text.length; i++) {
            body.append("<p>").append(text[i]).append("</p>");
        }
        return LetterPdf.render("Company", text[1], text[0], body.toString(), v);
    }
}
