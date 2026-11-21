package com.terp.erp.employee.certificate;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rich text of a letter template: sanitized on save (formatting tags only, alignment the only style kept) and
 * rendered to PDF by walking the HTML. Placeholders are replaced inside text nodes, so an employee's name can never
 * become markup.
 */
final class LetterPdf {
    static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private static final Safelist SAFE = new Safelist()
            .addTags("p", "div", "br", "span", "strong", "b", "em", "i", "u", "s", "strike", "del", "ul", "ol", "li",
                    "blockquote", "h1", "h2", "h3", "h4", "a", "pre", "code")
            .addAttributes(":all", "style", "class")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");
    private static final org.jsoup.nodes.Document.OutputSettings OUT =
            new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false);
    private static final Pattern ALIGN = Pattern.compile("text-align\\s*:\\s*(left|center|right|justify)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QL_ALIGN = Pattern.compile("ql-align-(left|center|right|justify)");
    private static final Set<String> BLOCKS = Set.of("p", "div", "blockquote", "h1", "h2", "h3", "h4", "pre");

    private LetterPdf() {
    }

    /** Formatting tags only; of style and class only the alignment survives (text-align or Quill's ql-align-*). */
    static String sanitize(String html) {
        var doc = Jsoup.parseBodyFragment(Jsoup.clean(html == null ? "" : html, "", SAFE, OUT));
        doc.outputSettings(OUT);
        for (var el : doc.body().select("[style], [class]")) {
            String align = align(el);
            el.removeAttr("style").removeAttr("class");
            if (align != null) {
                el.attr("style", "text-align: " + align);
            }
        }
        return doc.body().html().trim();
    }

    static byte[] render(String company, String dateLine, String title, String bodyHtml, Map<String, String> v) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BaseFont regular = font(FONT);
            BaseFont bold = font(FONT_BOLD);
            Document doc = new Document(PageSize.A4, 60, 60, 70, 70);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(company, new Font(bold, 13)));
            Paragraph date = new Paragraph(fill(dateLine, v), new Font(regular, 12));
            date.setAlignment(Element.ALIGN_RIGHT);
            date.setSpacingAfter(24);
            doc.add(date);
            Paragraph head = new Paragraph(title, new Font(bold, 16));
            head.setAlignment(Element.ALIGN_CENTER);
            head.setSpacingAfter(24);
            doc.add(head);
            new Walker(regular, bold, v).blocks(Jsoup.parseBodyFragment(bodyHtml).body(), doc);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    /** Embedded TTF: the image has no system fonts and the built-in PDF fonts have no Vietnamese glyphs. */
    static BaseFont font(String path) throws IOException {
        try (InputStream in = LetterPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }

    static String fill(String s, Map<String, String> v) {
        String out = s;
        for (var en : v.entrySet()) {
            out = out.replace("{" + en.getKey() + "}", en.getValue());
        }
        return out;
    }

    private static String align(org.jsoup.nodes.Element el) {
        Matcher m = ALIGN.matcher(el.attr("style"));
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        Matcher q = QL_ALIGN.matcher(el.attr("class"));
        return q.find() ? q.group(1) : null;
    }

    private record Walker(BaseFont regular, BaseFont bold, Map<String, String> v) {
        void blocks(org.jsoup.nodes.Element parent, Document doc) throws DocumentException {
            Paragraph loose = null;
            for (Node n : parent.childNodes()) {
                String tag = n instanceof org.jsoup.nodes.Element e ? e.normalName() : "";
                if (BLOCKS.contains(tag)) {
                    loose = flush(loose, doc);
                    doc.add(block((org.jsoup.nodes.Element) n, tag));
                } else if ("ul".equals(tag) || "ol".equals(tag)) {
                    loose = flush(loose, doc);
                    doc.add(list((org.jsoup.nodes.Element) n, "ol".equals(tag)));
                } else if (!(n instanceof TextNode t && t.isBlank())) {
                    // Inline content outside a paragraph (a bare text or <strong>): one paragraph until the next block.
                    if (loose == null) {
                        loose = paragraph(null);
                    }
                    inline(n, Font.NORMAL, 12, loose);
                }
            }
            flush(loose, doc);
        }

        private Paragraph flush(Paragraph p, Document doc) throws DocumentException {
            if (p != null) {
                doc.add(p);
            }
            return null;
        }

        private Paragraph block(org.jsoup.nodes.Element e, String tag) {
            Paragraph p = paragraph(align(e));
            float size = switch (tag) {
                case "h1" -> 18;
                case "h2" -> 16;
                case "h3" -> 14;
                default -> 12;
            };
            int style = tag.startsWith("h") ? Font.BOLD : Font.NORMAL;
            if ("blockquote".equals(tag)) {
                p.setIndentationLeft(24);
                style = Font.ITALIC;
            }
            for (Node c : e.childNodes()) {
                inline(c, style, size, p);
            }
            return p;
        }

        private com.lowagie.text.List list(org.jsoup.nodes.Element e, boolean ordered) {
            com.lowagie.text.List list = new com.lowagie.text.List(ordered, 16);
            list.setIndentationLeft(12);
            if (!ordered) {
                list.setListSymbol(new Chunk("•  ", new Font(regular, 12)));
            }
            for (var li : e.children()) {
                ListItem item = new ListItem();
                item.setFont(new Font(regular, 12));
                for (Node c : li.childNodes()) {
                    inline(c, Font.NORMAL, 12, item);
                }
                list.add(item);
            }
            return list;
        }

        private Paragraph paragraph(String align) {
            Paragraph p = new Paragraph();
            p.setFont(new Font(regular, 12));
            p.setSpacingAfter(10);
            p.setLeading(16);
            p.setAlignment(align == null ? Element.ALIGN_JUSTIFIED : switch (align) {
                case "center" -> Element.ALIGN_CENTER;
                case "right" -> Element.ALIGN_RIGHT;
                case "left" -> Element.ALIGN_LEFT;
                default -> Element.ALIGN_JUSTIFIED;
            });
            return p;
        }

        private void inline(Node n, int style, float size, Phrase target) {
            if (n instanceof TextNode t) {
                String text = fill(t.getWholeText().replaceAll("\\s+", " "), v);
                // Bold uses the real bold face; italic, underline and strike-through are font styles.
                BaseFont face = (style & Font.BOLD) != 0 ? bold : regular;
                target.add(new Chunk(text, new Font(face, size, style & ~Font.BOLD)));
                return;
            }
            if (!(n instanceof org.jsoup.nodes.Element e)) {
                return;
            }
            String tag = e.normalName();
            if ("br".equals(tag)) {
                target.add(Chunk.NEWLINE);
                return;
            }
            int s = style | switch (tag) {
                case "strong", "b" -> Font.BOLD;
                case "em", "i" -> Font.ITALIC;
                case "u", "a" -> Font.UNDERLINE;
                case "s", "strike", "del" -> Font.STRIKETHRU;
                default -> Font.NORMAL;
            };
            for (Node c : e.childNodes()) {
                inline(c, s, size, target);
            }
            if (BLOCKS.contains(tag) || "li".equals(tag)) {
                target.add(Chunk.NEWLINE);
            }
        }
    }
}
