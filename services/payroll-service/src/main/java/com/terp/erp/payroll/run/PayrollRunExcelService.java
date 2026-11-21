package com.terp.erp.payroll.run;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.exception.RestExceptionHandler;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.run.PayrollRun.EFrequency;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.run.PayrollRunService.RunForm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Export, import template and import of the Payroll Runs page. An imported row becomes a DRAFT run; rows with errors
 * are reported and skipped, the others saved.
 */
@Service
@RequiredArgsConstructor
public class PayrollRunExcelService {
    static final int MAX_ROWS = 500;
    static final String[] HEADERS = {"Title", "Frequency", "Pay Period Start (yyyy-MM-dd)",
            "Pay Period End (yyyy-MM-dd)", "Pay Date (yyyy-MM-dd)", "Notes"};
    private static final String[] EXPORT_HEADERS = {"Title", "Frequency", "Pay Period Start", "Pay Period End",
            "Pay Date", "Employees", "Gross Pay", "Total Deductions", "Net Pay", "Status", "Notes"};

    private final PayrollRunRepository runs;
    private final PayrollRunService service;
    private final RestExceptionHandler messages;

    public record RowError(int row, String code, String message) {
    }

    public record ImportResult(int rows, int created, List<RowError> errors) {
    }

    @Transactional(readOnly = true)
    public byte[] export(Long requestedCompanyId, String keyword, EStatus status) {
        List<PayrollRun> list = runs.search(Texts.company(requestedCompanyId), status, Texts.kw(keyword),
                Pageable.unpaged(Sort.by(Sort.Direction.DESC, "periodStart"))).getContent();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Payroll Runs");
            header(sh, EXPORT_HEADERS);
            int n = 1;
            for (PayrollRun r : list) {
                Row x = sh.createRow(n++);
                x.createCell(0).setCellValue(r.getTitle());
                x.createCell(1).setCellValue(r.getFrequency().name());
                x.createCell(2).setCellValue(r.getPeriodStart().toString());
                x.createCell(3).setCellValue(r.getPeriodEnd().toString());
                x.createCell(4).setCellValue(r.getPayDate().toString());
                x.createCell(5).setCellValue(r.getEmployeeCount());
                x.createCell(6).setCellValue(r.getGrossPay().doubleValue());
                x.createCell(7).setCellValue(r.getTotalDeductions().doubleValue());
                x.createCell(8).setCellValue(r.getNetPay().doubleValue());
                x.createCell(9).setCellValue(r.getStatus().name());
                x.createCell(10).setCellValue(r.getNotes() == null ? "" : r.getNotes());
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("export", e);
        }
    }

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Payroll Runs");
            header(sh, HEADERS);
            Row sample = sh.createRow(1);
            String[] values = {"November 2026", "MONTHLY", "2026-11-01", "2026-11-30", "2026-12-05",
                    "Sample row, replace it. Frequency: MONTHLY, SEMI_MONTHLY, BI_WEEKLY, WEEKLY"};
            for (int i = 0; i < values.length; i++) {
                sample.createCell(i).setCellValue(values[i]);
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("template", e);
        }
    }

    /** Not one transaction: each row is saved by its own create, so a refused row cannot roll the others back. */
    public ImportResult importFile(Long requestedCompanyId, MultipartFile file) {
        Texts.company(requestedCompanyId);
        List<Row> rows = rows(file);
        int created = 0;
        List<RowError> errors = new ArrayList<>();
        for (Row row : rows) {
            int n = row.getRowNum() + 1;
            try {
                String title = text(row, 0);
                if (title == null || title.length() > 200) {
                    throw rowError(n, "title");
                }
                String notes = text(row, 5);
                if (notes != null && notes.length() > 1000) {
                    throw rowError(n, "notes");
                }
                service.create(requestedCompanyId, new RunForm(title, frequency(row, n), date(row, 2, n, "start"),
                        date(row, 3, n, "end"), date(row, 4, n, "pay date"), notes));
                created++;
            } catch (ApiException ex) {
                errors.add(new RowError(n, ex.getErrorCode().getCode(),
                        messages.resolve(ex.getErrorCode(), ex.getArgs())));
            }
        }
        return new ImportResult(rows.size(), created, errors);
    }

    private static EFrequency frequency(Row row, int n) {
        String s = text(row, 1);
        try {
            return EFrequency.valueOf(s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                    .replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw rowError(n, "frequency");
        }
    }

    private static List<Row> rows(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sh = wb.getSheetAt(0);
            List<Row> out = new ArrayList<>();
            for (Row row : sh) {
                if (row.getRowNum() == 0 || blank(row)) {
                    continue;
                }
                out.add(row);
                if (out.size() > MAX_ROWS) {
                    throw ApiException.badRequest(EPayrollError.PAY_020, MAX_ROWS);
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) {
                throw a;
            }
            throw ApiException.badRequest(EPayrollError.PAY_019);
        }
    }

    private static boolean blank(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (row.getCell(i) != null && (text(row, i) != null || isDate(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDate(Cell c) {
        return c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c);
    }

    private static String text(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        String s = switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(c) ? null : String.valueOf((long) c.getNumericCellValue());
            default -> null;
        };
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate date(Row row, int i, int n, String field) {
        Cell c = row.getCell(i);
        try {
            if (c != null && isDate(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = text(row, i);
            if (s != null) {
                return LocalDate.parse(s);
            }
        } catch (DateTimeParseException ignored) {
            // reported below
        }
        throw rowError(n, field);
    }

    private static ApiException rowError(int row, String field) {
        return ApiException.badRequest(EPayrollError.PAY_021, row, field);
    }

    private static void header(Sheet sh, String[] headers) {
        Row h = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            sh.setColumnWidth(i, 22 * 256);
        }
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
