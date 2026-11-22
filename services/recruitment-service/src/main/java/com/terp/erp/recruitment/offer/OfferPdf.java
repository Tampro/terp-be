package com.terp.erp.recruitment.offer;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.jsoup.Jsoup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * An offer letter as an A4 PDF: the template's (already sanitized and filled) HTML reduced to text paragraphs, like
 * document-service's TemplatePdf (embedded TTF for Vietnamese). Markup is never interpreted, so nothing in the
 * template can reach the PDF as anything but text.
 */
final class OfferPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";
    private static final String BLOCKS = "p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote";

    private OfferPdf() {
    }

    static byte[] render(String title, String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 11);
            Paragraph h = new Paragraph(title, new Font(font(FONT_BOLD), 15));
            h.setAlignment(Element.ALIGN_CENTER);
            h.setSpacingAfter(14);
            doc.add(h);
            for (String para : paragraphs(html)) {
                Paragraph p = new Paragraph(para.isEmpty() ? " " : para, regular);
                p.setSpacingAfter(4);
                p.setAlignment(Element.ALIGN_JUSTIFIED);
                doc.add(p);
            }
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("offer letter pdf", e);
        }
    }

    /** One entry per block element / line break, entities decoded, whitespace collapsed. */
    static List<String> paragraphs(String html) {
        org.jsoup.nodes.Document d = Jsoup.parseBodyFragment(html == null ? "" : html);
        d.select("br").after(" ");
        d.select(BLOCKS).after(" ");
        String text = d.body().wholeText();
        List<String> out = new ArrayList<>();
        for (String block : text.split("[  ]")) {
            String line = block.replaceAll("\\s+", " ").trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static BaseFont font(String path) throws IOException {
        try (InputStream in = OfferPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
