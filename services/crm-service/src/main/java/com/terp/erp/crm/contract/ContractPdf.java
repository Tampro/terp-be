package com.terp.erp.crm.contract;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Contract PDF: the signed fields, the agreement text (tags stripped), each side's signature image, name and time
 * (UTC), the stored and current content hashes, and the disclaimer. Labels are English; Liberation TTF keeps
 * Vietnamese characters (as in accounting's PDFs).
 */
final class ContractPdf {
    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private static final String FONT_BOLD = "/liberation/LiberationSans-Bold.ttf";
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    private ContractPdf() {
    }

    static byte[] render(Contract c, String currentHash, List<ContractSignature> signatures) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font regular = new Font(font(FONT), 9);
            Font bold = new Font(font(FONT_BOLD), 9);
            Font small = new Font(font(FONT), 7);
            doc.add(new Paragraph("Contract " + c.getNumber(), new Font(font(FONT_BOLD), 14)));
            doc.add(new Paragraph(c.getSubject(), bold));
            PdfPTable info = new PdfPTable(new float[]{1, 3});
            info.setWidthPercentage(100);
            info.setSpacingBefore(8);
            pair(info, "Customer", c.getCustomerName(), regular, bold);
            pair(info, "Value", c.getValue() == null ? "" : c.getValue().stripTrailingZeros().toPlainString(),
                    regular, bold);
            pair(info, "Period", c.getStartDate() + " - " + c.getEndDate(), regular, bold);
            pair(info, "Status", c.getStatus().name(), regular, bold);
            doc.add(info);
            String text = plainText(c.getDescription());
            if (!text.isEmpty()) {
                Paragraph p = new Paragraph(text, regular);
                p.setSpacingBefore(10);
                doc.add(p);
            }

            Paragraph h = new Paragraph("Signatures", bold);
            h.setSpacingBefore(16);
            doc.add(h);
            PdfPTable sig = new PdfPTable(2);
            sig.setWidthPercentage(100);
            sig.setSpacingBefore(6);
            for (ContractSignature.ESignerType type : ContractSignature.ESignerType.values()) {
                ContractSignature s = signatures.stream().filter(x -> x.getSignerType() == type).findFirst()
                        .orElse(null);
                sig.addCell(signatureCell(type, s, currentHash, regular, bold, small));
            }
            doc.add(sig);

            Paragraph hash = new Paragraph("Content SHA-256 (" + ContractContentHash.VERSION + "): " + currentHash,
                    small);
            hash.setSpacingBefore(10);
            doc.add(hash);
            // TODO(assumption): legal wording of the disclaimer to be confirmed by BA.
            doc.add(new Paragraph(ContractSignatureService.DISCLAIMER, small));
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("contract pdf", e);
        }
    }

    private static PdfPCell signatureCell(ContractSignature.ESignerType type, ContractSignature s, String currentHash,
                                          Font regular, Font bold, Font small) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.setMinimumHeight(110);
        cell.addElement(new Paragraph(type == ContractSignature.ESignerType.COMPANY ? "Company" : "Client", bold));
        if (s == null) {
            cell.addElement(new Paragraph("Not signed", regular));
            return cell;
        }
        try {
            Image img = Image.getInstance(s.getSignaturePng());
            img.scaleToFit(200, 70);
            cell.addElement(img);
        } catch (IOException | RuntimeException e) {
            // Header-checked at signing, but a broken body must not prevent the rest of the PDF.
            cell.addElement(new Paragraph("[signature image unreadable]", small));
        }
        cell.addElement(new Paragraph(s.getSignerName(), regular));
        cell.addElement(new Paragraph("Signed " + AT.format(s.getSignedAt()), small));
        cell.addElement(new Paragraph("Signed content SHA-256: " + s.getContentSha256()
                + (currentHash.equals(s.getContentSha256()) ? " (matches)" : " (DIFFERS from current content)"),
                small));
        return cell;
    }

    private static void pair(PdfPTable t, String label, String value, Font regular, Font bold) {
        PdfPCell l = new PdfPCell(new Phrase(label, bold));
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, regular));
        l.setBorder(Rectangle.NO_BORDER);
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(l);
        t.addCell(v);
    }

    /** The editor's HTML as plain text: block ends become line breaks, tags dropped, common entities decoded. */
    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&")
                .strip();
    }

    private static BaseFont font(String path) throws IOException {
        try (InputStream in = ContractPdf.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("font missing: " + path);
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }
}
