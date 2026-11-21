package com.terp.erp.commerce.pos;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * POS receipt and barcode label sheet. Labels are English like the other generated files; names keep their
 * Vietnamese characters (embedded TTF).
 */
final class PosPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";
    private static final int LABEL_COLUMNS = 3;

    record Label(String name, String sku, BigDecimal price) {
    }

    private PosPdf() {
    }

    /** Itemised receipt: numbers, date, customer, warehouse, lines, subtotal, each tax, discount, total. */
    static byte[] receipt(PosService.OrderDetail d) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 9);
            Font bold = new Font(font(FONT_BOLD), 9);
            Paragraph h = new Paragraph("Receipt", new Font(font(FONT_BOLD), 14));
            h.setAlignment(Element.ALIGN_CENTER);
            h.setSpacingAfter(8);
            doc.add(h);

            PosService.OrderRow o = d.order();
            PdfPTable info = table(new float[]{1, 2});
            row(info, "Receipt No.", o.receiptNumber(), regular, bold);
            row(info, "POS No.", o.number(), regular, bold);
            row(info, "Date", o.orderDate().toString(), regular, bold);
            row(info, "Customer", o.customerName(), regular, bold);
            row(info, "Warehouse", o.warehouseName(), regular, bold);
            row(info, "Paid to", d.bankAccountName(), regular, bold);
            doc.add(info);

            PdfPTable lines = table(new float[]{4, 1.2f, 2, 2});
            for (String c : new String[]{"Item", "Qty", "Price", "Amount"}) {
                lines.addCell(cell(c, bold, Element.ALIGN_LEFT));
            }
            for (PosService.LineDto l : d.lines()) {
                lines.addCell(cell(l.itemName() + (l.sku() == null ? "" : "\n" + l.sku()), regular, Element.ALIGN_LEFT));
                lines.addCell(cell(qty(l.quantity()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.unitPrice()), regular, Element.ALIGN_RIGHT));
                lines.addCell(cell(money(l.amount()), regular, Element.ALIGN_RIGHT));
            }
            doc.add(lines);

            PdfPTable totals = table(new float[]{2, 1});
            row(totals, "Subtotal", money(d.subtotal()), regular, regular);
            for (PosService.TaxDto t : d.taxes()) {
                row(totals, t.name() + " (" + qty(t.rate()) + "%)", money(t.amount()), regular, regular);
            }
            row(totals, "Discount", "-" + money(d.discount()), regular, regular);
            row(totals, "Total", money(o.total()), bold, bold);
            doc.add(totals);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("pos receipt pdf", ex);
        }
    }

    /** A4 sheet, 3 labels per row: name, Code 128 of the SKU, price. */
    static byte[] barcodes(List<Label> labels) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 20, 20, 20, 20);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 8);
            Font bold = new Font(font(FONT_BOLD), 8);
            PdfContentByte cb = writer.getDirectContent();
            PdfPTable grid = new PdfPTable(LABEL_COLUMNS);
            grid.setWidthPercentage(100);
            for (Label l : labels) {
                Barcode128 code = new Barcode128();
                code.setCode(l.sku());
                code.setBarHeight(30);
                Image img = code.createImageWithBarcode(cb, null, null);
                PdfPTable inner = new PdfPTable(1);
                inner.addCell(bare(new Phrase(l.name(), bold)));
                PdfPCell bar = new PdfPCell(img, false);
                bar.setBorder(PdfPCell.NO_BORDER);
                bar.setHorizontalAlignment(Element.ALIGN_CENTER);
                bar.setPadding(3);
                inner.addCell(bar);
                inner.addCell(bare(new Phrase(money(l.price()), regular)));
                PdfPCell c = new PdfPCell(inner);
                c.setPadding(6);
                grid.addCell(c);
            }
            grid.completeRow();
            doc.add(grid);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("barcode pdf", ex);
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

    private static PdfPCell bare(Phrase p) {
        PdfPCell c = new PdfPCell(p);
        c.setBorder(PdfPCell.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private static void row(PdfPTable t, String label, String value, Font lf, Font vf) {
        t.addCell(cell(label, lf, Element.ALIGN_LEFT));
        t.addCell(cell(value == null ? "" : value, vf, Element.ALIGN_RIGHT));
    }

    static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
    }

    private static String qty(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Embedded TTF: the image has no system fonts and the built-in PDF fonts have no Vietnamese glyphs. */
    private static BaseFont font(String path) throws IOException {
        try (InputStream in = PosPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
