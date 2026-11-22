package com.terp.erp.accounting.report;

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
import com.terp.erp.accounting.report.ReportService.AgingReport;
import com.terp.erp.accounting.report.ReportService.AgingRow;
import com.terp.erp.accounting.report.ReportService.DocLine;
import com.terp.erp.accounting.report.ReportService.PartyReport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Aging and customer / vendor report PDFs. Labels are English; names keep Vietnamese characters (embedded TTF). */
final class ReportPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private ReportPdf() {
    }

    static byte[] aging(AgingReport r) {
        String title = (r.party() == ReportService.EParty.CUSTOMER ? "Invoice aging" : "Bill aging")
                + " as of " + r.asOf();
        return write(PageSize.A4.rotate(), title, (doc, regular, bold) -> {
            PdfPTable t = table(new float[]{4, 2, 2, 2, 2, 2, 2});
            for (String h : new String[]{r.party() == ReportService.EParty.CUSTOMER ? "Customer" : "Vendor",
                    "Current", "1-30", "31-60", "61-90", "Over 90", "Total"}) {
                t.addCell(cell(h, bold, Element.ALIGN_LEFT));
            }
            for (AgingRow a : r.rows()) {
                agingRow(t, a, regular);
            }
            agingRow(t, r.totals(), bold);
            doc.add(t);
        });
    }

    static byte[] party(PartyReport r) {
        String kind = r.party() == ReportService.EParty.CUSTOMER ? "Customer" : "Vendor";
        String title = kind + " report: " + r.name() + " (" + r.code() + ")";
        return write(PageSize.A4, title, (doc, regular, bold) -> {
            doc.add(new Paragraph("Period " + r.from() + " to " + r.to(), regular));
            PdfPTable sum = table(new float[]{3, 2});
            boolean customer = r.party() == ReportService.EParty.CUSTOMER;
            summary(sum, customer ? "Invoiced" : "Billed", r.invoiced(), regular);
            summary(sum, "Returns", r.returns(), regular);
            summary(sum, customer ? "Credit notes" : "Debit notes", r.notes(), regular);
            summary(sum, "Payments", r.payments(), regular);
            summary(sum, "Balance (today)", r.balance(), bold);
            doc.add(sum);
            section(doc, customer ? "Invoices" : "Bills", r.invoices(), regular, bold);
            section(doc, "Returns", r.returnDocs(), regular, bold);
            section(doc, customer ? "Credit notes" : "Debit notes", r.noteDocs(), regular, bold);
            section(doc, "Payments", r.paymentDocs(), regular, bold);
        });
    }

    private interface Body {
        void add(Document doc, Font regular, Font bold) throws DocumentException;
    }

    private static byte[] write(com.lowagie.text.Rectangle size, String title, Body body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(size, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 9);
            Font bold = new Font(font(FONT_BOLD), 9);
            Paragraph h = new Paragraph(title, new Font(font(FONT_BOLD), 14));
            h.setSpacingAfter(8);
            doc.add(h);
            body.add(doc, regular, bold);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("report PDF failed", e);
        }
    }

    private static void section(Document doc, String title, List<DocLine> lines, Font regular, Font bold)
            throws DocumentException {
        Paragraph p = new Paragraph(title, bold);
        p.setSpacingBefore(8);
        doc.add(p);
        PdfPTable t = table(new float[]{3, 2, 2, 2});
        for (String h : new String[]{"Number", "Date", "Status", "Amount"}) {
            t.addCell(cell(h, bold, Element.ALIGN_LEFT));
        }
        for (DocLine l : lines) {
            t.addCell(cell(l.number(), regular, Element.ALIGN_LEFT));
            t.addCell(cell(l.date() == null ? "" : l.date().toString(), regular, Element.ALIGN_LEFT));
            t.addCell(cell(l.status(), regular, Element.ALIGN_LEFT));
            t.addCell(cell(money(l.amount()), regular, Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    private static void agingRow(PdfPTable t, AgingRow a, Font f) {
        t.addCell(cell(a.partyName(), f, Element.ALIGN_LEFT));
        for (BigDecimal v : new BigDecimal[]{a.current(), a.days1To30(), a.days31To60(), a.days61To90(), a.over90(),
                a.total()}) {
            t.addCell(cell(money(v), f, Element.ALIGN_RIGHT));
        }
    }

    private static void summary(PdfPTable t, String label, BigDecimal v, Font f) {
        t.addCell(cell(label, f, Element.ALIGN_LEFT));
        t.addCell(cell(money(v), f, Element.ALIGN_RIGHT));
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        return t;
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(3);
        return c;
    }

    private static String money(BigDecimal v) {
        DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(v == null ? BigDecimal.ZERO : v);
    }

    private static BaseFont font(String path) throws IOException {
        try (InputStream in = ReportPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
