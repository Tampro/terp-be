package com.terp.erp.accounting.ledger;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.terp.erp.accounting.ledger.BalanceSheetService.GroupDto;
import com.terp.erp.accounting.ledger.BalanceSheetService.LineDto;
import com.terp.erp.accounting.ledger.BalanceSheetService.SectionDto;
import com.terp.erp.accounting.ledger.BalanceSheetService.SheetDto;
import com.terp.erp.accounting.ledger.LedgerReportService.AccountBalanceRow;
import com.terp.erp.accounting.ledger.LedgerReportService.AccountBalances;
import com.terp.erp.accounting.ledger.LedgerReportService.AmountGroup;
import com.terp.erp.accounting.ledger.LedgerReportService.AmountLine;
import com.terp.erp.accounting.ledger.LedgerReportService.CashFlow;
import com.terp.erp.accounting.ledger.LedgerReportService.CashFlowLine;
import com.terp.erp.accounting.ledger.LedgerReportService.CashFlowSection;
import com.terp.erp.accounting.ledger.LedgerReportService.ExpenseReport;
import com.terp.erp.accounting.ledger.LedgerReportService.GeneralLedger;
import com.terp.erp.accounting.ledger.LedgerReportService.LedgerRow;
import com.terp.erp.accounting.ledger.LedgerReportService.ProfitLoss;
import com.terp.erp.accounting.ledger.LedgerReportService.TrialBalance;
import com.terp.erp.accounting.ledger.LedgerReportService.TrialBalanceRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDFs of the double-entry reports. Labels are English; account names keep Vietnamese characters (embedded
 * Liberation TTF, as in the accounting report package).
 */
final class LedgerPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";

    private LedgerPdf() {
    }

    /** One table row; bold rows are totals / headings. */
    private record Row(boolean bold, String... cells) {
    }

    static byte[] trialBalance(TrialBalance r) {
        List<Row> rows = new ArrayList<>();
        for (TrialBalanceRow t : r.rows()) {
            rows.add(tbRow(false, t));
        }
        rows.add(tbRow(true, r.totals()));
        return table(PageSize.A4.rotate(), "Trial balance", period(r.from(), r.to())
                        + (r.balanced() ? " - balanced" : " - NOT balanced, difference " + money(r.difference())),
                new String[]{"Code", "Account", "Opening Dr", "Opening Cr", "Debit", "Credit", "Closing Dr",
                        "Closing Cr"}, new float[]{2, 5, 2, 2, 2, 2, 2, 2}, rows);
    }

    static byte[] profitLoss(ProfitLoss r) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(true, "", "Revenue", ""));
        groups(rows, r.revenue());
        rows.add(new Row(true, "", "Total revenue", money(r.totalRevenue())));
        rows.add(new Row(true, "", "Expenses", ""));
        groups(rows, r.expenses());
        rows.add(new Row(true, "", "Total expenses", money(r.totalExpenses())));
        rows.add(new Row(true, "", r.netProfit().signum() >= 0 ? "Net profit" : "Net loss", money(r.netProfit())));
        return table(PageSize.A4, "Profit & loss", period(r.from(), r.to()), new String[]{"Code", "Account",
                "Amount"}, new float[]{2, 6, 3}, rows);
    }

    static byte[] expenseReport(ExpenseReport r) {
        List<Row> rows = new ArrayList<>();
        groups(rows, r.categories());
        rows.add(new Row(true, "", "Total expenses", money(r.total())));
        return table(PageSize.A4, "Expense report", period(r.from(), r.to()), new String[]{"Code", "Account",
                "Amount"}, new float[]{2, 6, 3}, rows);
    }

    static byte[] cashFlow(CashFlow r) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(true, "", "Opening cash", "", "", money(r.openingCash())));
        for (CashFlowSection s : r.sections()) {
            rows.add(new Row(true, "", s.activity().name().charAt(0) + s.activity().name().substring(1)
                    .toLowerCase() + " activities", "", "", ""));
            for (CashFlowLine l : s.lines()) {
                rows.add(new Row(false, l.code(), l.name(), money(l.inflow()), money(l.outflow()), money(l.net())));
            }
            rows.add(new Row(true, "", "Net " + s.activity().name().toLowerCase(), money(s.inflow()),
                    money(s.outflow()), money(s.net())));
        }
        rows.add(new Row(true, "", "Net change", "", "", money(r.netChange())));
        rows.add(new Row(true, "", "Closing cash", "", "", money(r.closingCash())));
        return table(PageSize.A4, "Cash flow", period(r.from(), r.to()) + " - cash accounts "
                + String.join(", ", r.cashAccountCodes()), new String[]{"Code", "Counterpart", "Inflow",
                "Outflow", "Net"}, new float[]{2, 5, 2, 2, 2}, rows);
    }

    static byte[] ledger(GeneralLedger r, boolean statement) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(true, "", "", "Opening balance", "", "", "", money(r.openingBalance())));
        for (LedgerRow l : r.lines()) {
            String ref = statement
                    ? String.join(", ", l.counterpartCodes() == null ? List.of() : l.counterpartCodes())
                    : (l.reference() == null ? "" : l.reference());
            rows.add(new Row(false, String.valueOf(l.date()), l.number(), l.description(), ref, money(l.debit()),
                    money(l.credit()), money(l.balance())));
        }
        rows.add(new Row(true, "", "", "Totals / closing balance", "", money(r.totalDebit()),
                money(r.totalCredit()), money(r.closingBalance())));
        return table(PageSize.A4.rotate(), (statement ? "Account statement: " : "General ledger: ") + r.code()
                        + " " + r.name(), period(r.from(), r.to()) + " - normal balance " + r.normalBalance(),
                new String[]{"Date", "Entry", "Description", statement ? "Counterpart" : "Reference", "Debit",
                        "Credit", "Balance"}, new float[]{2, 2.5f, 6, 2.5f, 2, 2, 2}, rows);
    }

    static byte[] accountBalances(AccountBalances r) {
        List<Row> rows = new ArrayList<>();
        for (AccountBalanceRow a : r.rows()) {
            rows.add(new Row(false, a.code(), a.name(), a.category() == null ? "" : a.category().name(),
                    a.normalBalance().name(), money(a.balance())));
        }
        return table(PageSize.A4, "Account balances", "As of " + r.asOf(), new String[]{"Code", "Account",
                "Category", "Normal", "Balance"}, new float[]{2, 6, 2.5f, 1.5f, 3}, rows);
    }

    static byte[] balanceSheet(SheetDto s) {
        List<Row> rows = new ArrayList<>();
        for (SectionDto section : s.sections()) {
            rows.add(new Row(true, "", section.section().name(), ""));
            for (GroupDto g : section.groups()) {
                rows.add(new Row(true, "", g.group().replace('_', ' ').toLowerCase(), ""));
                for (LineDto l : g.lines()) {
                    rows.add(new Row(false, l.accountCode(), l.accountName(), money(l.amount())));
                }
                rows.add(new Row(false, "", "Subtotal", money(g.total())));
            }
            rows.add(new Row(true, "", "Total " + section.section().name().toLowerCase(), money(section.total())));
        }
        rows.add(new Row(true, "", "Assets - (liabilities + equity)", money(s.difference())));
        return table(PageSize.A4, "Balance sheet as of " + s.date(), "Financial year " + s.financialYear()
                        + " - " + s.status() + (s.notes() == null ? "" : " - " + s.notes()),
                new String[]{"Code", "Account", "Amount"}, new float[]{2, 6, 3}, rows);
    }

    // ---------- helpers ----------

    private static Row tbRow(boolean bold, TrialBalanceRow t) {
        return new Row(bold, t.code(), t.name(), money(t.openingDebit()), money(t.openingCredit()),
                money(t.periodDebit()), money(t.periodCredit()), money(t.closingDebit()), money(t.closingCredit()));
    }

    private static void groups(List<Row> rows, List<AmountGroup> groups) {
        for (AmountGroup g : groups) {
            rows.add(new Row(true, g.typeCode(), g.typeName(), ""));
            for (AmountLine l : g.lines()) {
                rows.add(new Row(false, l.code(), l.name(), money(l.amount())));
            }
            rows.add(new Row(false, "", "Subtotal", money(g.total())));
        }
    }

    private static String period(Object from, Object to) {
        return "Period " + from + " to " + to;
    }

    /** First two columns left-aligned text, the rest right-aligned amounts. */
    private static byte[] table(Rectangle size, String title, String subtitle, String[] headers, float[] widths,
                                List<Row> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(size, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 8);
            Font bold = new Font(font(FONT_BOLD), 8);
            Paragraph h = new Paragraph(title, new Font(font(FONT_BOLD), 14));
            h.setSpacingAfter(4);
            doc.add(h);
            doc.add(new Paragraph(subtitle, regular));
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setSpacingBefore(6);
            t.setHeaderRows(1);
            for (int i = 0; i < headers.length; i++) {
                t.addCell(cell(headers[i], bold, align(i, headers.length)));
            }
            for (Row r : rows) {
                for (int i = 0; i < headers.length; i++) {
                    t.addCell(cell(i < r.cells().length ? r.cells()[i] : "", r.bold() ? bold : regular,
                            align(i, headers.length)));
                }
            }
            doc.add(t);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("ledger PDF failed", e);
        }
    }

    private static int align(int column, int columns) {
        // Ledger rows have 3 text columns before the amounts.
        int text = columns == 7 ? 4 : 2;
        return column < text ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
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
        try (InputStream in = LedgerPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
