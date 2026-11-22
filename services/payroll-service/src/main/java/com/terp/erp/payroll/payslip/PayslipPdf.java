package com.terp.erp.payroll.payslip;

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
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.run.PayrollEntry;
import com.terp.erp.payroll.run.PayrollRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The payslip PDF: employee and period, earnings and deductions side by side, then the final calculation down to the
 * net pay. Labels are English like the other generated files; names keep their Vietnamese characters (embedded TTF).
 */
final class PayslipPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private PayslipPdf() {
    }

    static byte[] render(Payslip p, PayrollEntry e, PayrollRun r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 10);
            Font bold = new Font(font(FONT_BOLD), 10);
            Font title = new Font(font(FONT_BOLD), 16);
            Paragraph h = new Paragraph("Payslip", title);
            h.setAlignment(Element.ALIGN_CENTER);
            doc.add(h);
            Paragraph sub = new Paragraph(r.getTitle(), regular);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);

            PdfPTable info = table(2);
            row(info, "Employee", nz(p.getEmployeeName()), regular, bold);
            row(info, "Employee No.", nz(p.getEmployeeNo()), regular, bold);
            row(info, "Pay Period", p.getPeriodStart() + " – " + p.getPeriodEnd(), regular, bold);
            row(info, "Pay Date", p.getPayDate().toString(), regular, bold);
            row(info, "Working Days / Present / LOP", e.getWorkingDays() + " / " + e.getPresentDays().stripTrailingZeros()
                    .toPlainString() + " / " + e.getLopDays().stripTrailingZeros().toPlainString(), regular, bold);
            doc.add(info);

            PdfPTable earn = table(2);
            header(earn, "Earnings", bold);
            row(earn, "Basic Salary", money(e.getBasicSalary()), regular, regular);
            PdfPTable ded = table(2);
            header(ded, "Deductions", bold);
            for (PayrollEntry.Line l : e.getLines()) {
                row(l.getType() == EType.EARNING ? earn : ded, l.getName(), money(l.getAmount()), regular, regular);
            }
            row(earn, "Total Earnings", money(e.getTotalEarnings()), bold, bold);
            row(ded, "Total Deductions", money(e.getTotalDeductions()), bold, bold);
            PdfPTable sides = table(2);
            sides.addCell(plain(earn));
            sides.addCell(plain(ded));
            doc.add(sides);

            PdfPTable calc = table(2);
            header(calc, "Final Calculation", bold);
            row(calc, "Total Earnings", money(e.getTotalEarnings()), regular, regular);
            row(calc, "− LOP Deduction", money(e.getLopDeduction()), regular, regular);
            row(calc, "− Unpaid Leave Deduction", money(e.getUnpaidLeaveDeduction()), regular, regular);
            row(calc, "+ Attendance Overtime", money(e.getOvertimeAmount()), regular, regular);
            row(calc, "+ Manual Overtime", money(e.getManualOvertimeAmount()), regular, regular);
            row(calc, "Gross Pay", money(e.getGrossPay()), bold, bold);
            row(calc, "− Component Deductions", money(e.getTotalDeductions()), regular, regular);
            row(calc, "− Loan Deduction", money(e.getLoanDeduction()), regular, regular);
            row(calc, "Net Salary (Take Home)", money(e.getNetPay()), bold, bold);
            doc.add(calc);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("payslip pdf", ex);
        }
    }

    private static PdfPTable table(int cols) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        return t;
    }

    private static PdfPCell plain(PdfPTable inner) {
        PdfPCell c = new PdfPCell(inner);
        c.setBorder(PdfPCell.NO_BORDER);
        c.setPadding(2);
        return c;
    }

    private static void header(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(2);
        c.setPadding(5);
        t.addCell(c);
    }

    private static void row(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell l = new PdfPCell(new Phrase(label, lf));
        l.setPadding(4);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, vf));
        v.setPadding(4);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(v);
    }

    static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Embedded TTF: the image has no system fonts and the built-in PDF fonts have no Vietnamese glyphs. */
    private static BaseFont font(String path) throws IOException {
        try (InputStream in = PayslipPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
