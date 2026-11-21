package com.terp.erp.document.template;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** A filled template as an A4 PDF: the title, then the text paragraph by paragraph (embedded TTF for Vietnamese). */
final class TemplatePdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private TemplatePdf() {
    }

    static byte[] render(String title, String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 11);
            Paragraph h = new Paragraph(title, new Font(font(FONT_BOLD), 15));
            h.setAlignment(Element.ALIGN_CENTER);
            h.setSpacingAfter(14);
            doc.add(h);
            for (String para : text.replace("\r\n", "\n").split("\n")) {
                Paragraph p = new Paragraph(para.isEmpty() ? " " : para, regular);
                p.setSpacingAfter(4);
                p.setAlignment(Element.ALIGN_JUSTIFIED);
                doc.add(p);
            }
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("template pdf", e);
        }
    }

    private static BaseFont font(String path) throws IOException {
        try (InputStream in = TemplatePdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
