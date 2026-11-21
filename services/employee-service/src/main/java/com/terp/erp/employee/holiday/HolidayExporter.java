package com.terp.erp.employee.holiday;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.terp.erp.employee.certificate.LetterService;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayDto;
import com.terp.erp.employee.holiday.HolidayDtos.Ref;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Holiday list as PDF (A4 landscape table) and as iCalendar (RFC 5545, all-day events). */
final class HolidayExporter {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ICAL_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ICAL_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String ALL_BRANCHES = "All branches";

    private HolidayExporter() {
    }

    static byte[] pdf(List<HolidayDto> rows, String subtitle) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(LetterService.font(LetterService.FONT), 10);
            Font bold = new Font(LetterService.font(LetterService.FONT_BOLD), 10);
            Paragraph title = new Paragraph("Holidays", new Font(LetterService.font(LetterService.FONT_BOLD), 16));
            doc.add(title);
            Paragraph sub = new Paragraph(subtitle, regular);
            sub.setSpacingAfter(12);
            doc.add(sub);
            PdfPTable table = new PdfPTable(new float[]{3.2f, 2.2f, 1.8f, 3.8f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{"Holiday Name", "Date", "Category", "Branches"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, bold));
                c.setPadding(5);
                table.addCell(c);
            }
            for (HolidayDto r : rows) {
                cell(table, r.name(), regular);
                cell(table, dates(r), regular);
                cell(table, r.category() == null ? "-" : r.category().name(), regular);
                cell(table, branches(r), regular);
            }
            if (rows.isEmpty()) {
                PdfPCell c = new PdfPCell(new Phrase("No holidays", regular));
                c.setColspan(4);
                c.setPadding(5);
                table.addCell(c);
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    static byte[] ical(List<HolidayDto> rows, Instant now) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//TERP//ERP Holidays//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        String stamp = ICAL_STAMP.format(now);
        for (HolidayDto r : rows) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:holiday-" + r.id() + "@erp.specom.vn");
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART;VALUE=DATE:" + ICAL_DATE.format(r.startDate()));
            // DTEND of an all-day event is exclusive.
            line(sb, "DTEND;VALUE=DATE:" + ICAL_DATE.format(r.endDate().plusDays(1)));
            line(sb, "SUMMARY:" + text(r.name()));
            if (r.category() != null) {
                line(sb, "CATEGORIES:" + text(r.category().name()));
            }
            String desc = (r.description() == null ? "" : r.description() + "\n") + "Branches: " + branches(r);
            line(sb, "DESCRIPTION:" + text(desc));
            line(sb, "TRANSP:TRANSPARENT");
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Second line of the PDF: the date range the filters resolve to. */
    static String subtitle(HolidayDtos.Filter f) {
        java.time.LocalDate[] r = HolidayService.range(f);
        if (r[0] == null && r[1] == null) {
            return "All dates";
        }
        return (r[0] == null ? "..." : DAY.format(r[0])) + " - " + (r[1] == null ? "..." : DAY.format(r[1]));
    }

    static String fileName(HolidayDtos.Filter f, String ext) {
        return "holidays" + (f.year() == null ? "" : "-" + f.year()) + "." + ext;
    }

    static String dates(HolidayDto r) {
        return r.startDate().equals(r.endDate()) ? DAY.format(r.startDate())
                : DAY.format(r.startDate()) + " - " + DAY.format(r.endDate());
    }

    static String branches(HolidayDto r) {
        return r.allBranches() ? ALL_BRANCHES : r.branches().stream().map(Ref::name).collect(Collectors.joining(", "));
    }

    /** RFC 5545 §3.3.11 TEXT escaping. */
    static String text(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /** CRLF line ends, folded at 75 octets without splitting a UTF-8 character (RFC 5545 §3.1). */
    static void line(StringBuilder sb, String content) {
        int octets = 0;
        int i = 0;
        while (i < content.length()) {
            int cp = content.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + len > 75) {
                sb.append("\r\n ");
                octets = 1;
            }
            sb.appendCodePoint(cp);
            octets += len;
            i += Character.charCount(cp);
        }
        sb.append("\r\n");
    }

    private static void cell(PdfPTable t, String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setPadding(5);
        t.addCell(c);
    }
}
