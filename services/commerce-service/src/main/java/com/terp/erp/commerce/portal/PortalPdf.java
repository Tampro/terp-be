package com.terp.erp.commerce.portal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.terp.erp.commerce.portal.PortalDtos.DocLine;
import com.terp.erp.commerce.portal.PortalDtos.ReturnDetail;
import com.terp.erp.commerce.portal.PortalDtos.ReturnLine;
import com.terp.erp.commerce.portal.PortalDtos.SalesDetail;
import com.terp.erp.commerce.portal.PortalDtos.SalesRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Printable copy of a portal document, built from the portal DTO only (so the PDF can never show more than the
 * JSON). English labels like the other generated files; names keep their Vietnamese characters (embedded TTF).
 */
final class PortalPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private PortalPdf() {
    }

    static byte[] sales(SalesDetail d, String customerName) {
        SalesRow r = d.document();
        String title = switch (r.kind()) {
            case QUOTATION -> "Quotation";
            case PROPOSAL -> "Proposal";
            case INVOICE -> "Invoice";
        };
        return build(title, (doc, regular, bold) -> {
            PdfPTable info = table(new float[]{1, 2});
            row(info, "Number", r.number(), regular, bold);
            row(info, "Customer", customerName, regular, bold);
            row(info, "Date", r.docDate().toString(), regular, bold);
            row(info, r.kind() == com.terp.erp.commerce.sales.EDocumentKind.INVOICE ? "Due date" : "Valid until",
                    r.dueDate().toString(), regular, bold);
            row(info, "Status", r.status().name(), regular, bold);
            if (d.paymentTerms() != null) {
                row(info, "Payment terms", d.paymentTerms(), regular, bold);
            }
            doc.add(info);
            PdfPTable lines = table(new float[]{4, 1.2f, 2, 1.2f, 1.2f, 2});
            for (String c : new String[]{"Item", "Qty", "Price", "Disc. %", "Tax %", "Amount"}) {
                lines.addCell(cell(c, bold, Element.ALIGN_LEFT));
            }
            for (DocLine l : d.lines()) {
                lines.addCell(cell(l.itemName() + (l.sku() == null ? "" : "\n" + l.sku()), regular, Element.ALIGN_LEFT));
                lines.addCell(cell(qty(l.quantity()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.unitPrice()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(qty(l.discountPct()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(qty(l.taxRate()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.lineTotal()), regular, Element.ALIGN_RIGHT));
            }
            doc.add(lines);
            PdfPTable totals = table(new float[]{2, 1});
            row(totals, "Subtotal", money(r.subtotal()), regular, regular);
            row(totals, "Discount", "-" + money(r.discount()), regular, regular);
            row(totals, "Tax", money(r.tax()), regular, regular);
            row(totals, "Total", money(r.total()), bold, bold);
            if (r.balance() != null) {
                row(totals, "Balance due", money(r.balance()), bold, bold);
            }
            doc.add(totals);
        });
    }

    static byte[] salesReturn(ReturnDetail d, String customerName) {
        PortalDtos.ReturnRow r = d.ret();
        return build("Sales return", (doc, regular, bold) -> {
            PdfPTable info = table(new float[]{1, 2});
            row(info, "Number", r.number(), regular, bold);
            row(info, "Customer", customerName, regular, bold);
            row(info, "Return date", r.returnDate().toString(), regular, bold);
            row(info, "Status", r.status(), regular, bold);
            if (d.reason() != null) {
                row(info, "Reason", d.reason(), regular, bold);
            }
            doc.add(info);
            PdfPTable lines = table(new float[]{4, 1.2f, 2, 2, 3});
            for (String c : new String[]{"Item", "Qty", "Price", "Amount", "Reason"}) {
                lines.addCell(cell(c, bold, Element.ALIGN_LEFT));
            }
            for (ReturnLine l : d.lines()) {
                lines.addCell(cell(l.itemName() + (l.sku() == null ? "" : "\n" + l.sku()), regular, Element.ALIGN_LEFT));
                lines.addCell(cell(qty(l.quantity()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.unitPrice()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.lineTotal()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(l.reason() == null ? "" : l.reason(), regular, Element.ALIGN_LEFT));
            }
            doc.add(lines);
            PdfPTable totals = table(new float[]{2, 1});
            row(totals, "Subtotal", money(r.subtotal()), regular, regular);
            row(totals, "Discount", "-" + money(r.discount()), regular, regular);
            row(totals, "Tax", money(r.tax()), regular, regular);
            row(totals, "Total", money(r.total()), bold, bold);
            doc.add(totals);
        });
    }

    private interface Body {
        void write(Document doc, Font regular, Font bold) throws DocumentException;
    }

    private static byte[] build(String title, Body body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 9);
            Font bold = new Font(font(FONT_BOLD), 9);
            Paragraph h = new Paragraph(title, new Font(font(FONT_BOLD), 16));
            h.setAlignment(Element.ALIGN_CENTER);
            h.setSpacingAfter(10);
            doc.add(h);
            body.write(doc, regular, bold);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("portal pdf", ex);
        }
    }

    private static PdfPTable table(float[] widths) throws DocumentException {
        PdfPTable t = new PdfPTable(widths.length);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        t.setSpacingAfter(8);
        return t;
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(3);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static void row(PdfPTable t, String label, String value, Font lf, Font vf) {
        t.addCell(cell(label, lf, Element.ALIGN_LEFT));
        t.addCell(cell(value == null ? "" : value, vf, Element.ALIGN_RIGHT));
    }

    private static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US))
                .format(v == null ? BigDecimal.ZERO : v);
    }

    private static String qty(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /** Embedded TTF: the image has no system fonts and the built-in PDF fonts have no Vietnamese glyphs. */
    private static BaseFont font(String path) throws IOException {
        try (InputStream in = PortalPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
