package com.terp.erp.attendance.timesheet;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryForm;
import com.terp.erp.attendance.timesheet.TimesheetDtos.ImportResult;
import com.terp.erp.attendance.timesheet.TimesheetDtos.RowError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Timesheet import (CSV or .xlsx, docs/analysis/06 §6.4.2: "employee names in the file exactly match") and export.
 * Columns: Employee, Date, Hours, Project, Description. The employee is matched by its exact full name, or by its
 * Employee Code / Employee No (a name shared by several employees must use the code: ATT_033). Every row creates an
 * entry (PENDING); rows with errors are reported and skipped.
 */
@Service
@RequiredArgsConstructor
public class TimesheetFileService {
    static final int MAX_ROWS = 2000;
    static final int MAX_EXPORT_DAYS = 366;
    static final String[] HEADERS = {"Employee", "Date (yyyy-MM-dd)", "Hours", "Project", "Description"};

    private final TimesheetService service;
    private final TimeEntryRepository entries;
    private final EmployeeClient employees;
    private final RestExceptionHandler messages;

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Timesheet");
            Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                h.createCell(i).setCellValue(HEADERS[i]);
                sh.setColumnWidth(i, 24 * 256);
            }
            Row sample = sh.createRow(1);
            String[] v = {"Test Person", "2026-11-18", "6.5", "ERP portal", "Sample row, replace it"};
            for (int i = 0; i < v.length; i++) {
                sample.createCell(i).setCellValue(v[i]);
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("template", e);
        }
    }

    @Transactional
    public ImportResult importFile(Long requestedCompanyId, MultipartFile file) {
        Long companyId = TimesheetService.company(requestedCompanyId);
        List<String[]> rows = read(file);
        List<Employee> all = employees.all(requestedCompanyId);
        Map<String, List<Employee>> byName = all.stream().filter(e -> e.fullName() != null)
                .collect(Collectors.groupingBy(e -> e.fullName().trim()));
        Map<String, Employee> byCode = new HashMap<>();
        for (Employee e : all) {
            for (String c : new String[]{e.employeeCode(), e.employeeNo()}) {
                if (c != null && !c.isBlank()) {
                    byCode.putIfAbsent(c.trim().toLowerCase(Locale.ROOT), e);
                }
            }
        }
        int created = 0;
        List<RowError> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int n = i + 2;
            String[] r = rows.get(i);
            try {
                Employee e = employee(r[0], byName, byCode, n);
                LocalDate date = date(r[1], n);
                BigDecimal hours = hours(r[2], n);
                if (r[4] == null || r[4].isBlank()) {
                    throw rowError(n, "description");
                }
                service.save(new TimeEntry(), companyId,
                        new EntryForm(Long.valueOf(e.id()), date, hours, r[3], r[4].length() > 1000
                                ? r[4].substring(0, 1000) : r[4]));
                created++;
            } catch (ApiException ex) {
                errors.add(new RowError(n, ex.getErrorCode().getCode(), messages.resolve(ex.getErrorCode(), ex.getArgs())));
            }
        }
        return new ImportResult(rows.size(), created, errors);
    }

    /** Entries of [from, to] (at most a year), one row each. */
    @Transactional(readOnly = true)
    public byte[] export(Long requestedCompanyId, LocalDate from, LocalDate to, Long employeeId,
                         ERegularizationStatus status) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_EXPORT_DAYS) {
            throw ApiException.badRequest(com.terp.erp.exception.ECommonError.COMMON_001);
        }
        Long companyId = TimesheetService.company(requestedCompanyId);
        Map<String, Employee> people = employees.all(requestedCompanyId).stream()
                .collect(Collectors.toMap(Employee::id, e -> e, (a, b) -> a));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Timesheet");
            String[] head = {"Employee", "Code", "Date", "Hours", "Project", "Description", "Status", "Comments"};
            Row h = sh.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            int r = 1;
            for (TimeEntry t : entries.export(companyId, employeeId, from, to, status)) {
                Employee e = people.get(t.getEmployeeId().toString());
                Row x = sh.createRow(r++);
                x.createCell(0).setCellValue(e == null ? "" : e.fullName());
                x.createCell(1).setCellValue(e == null ? "" : e.employeeCode() != null ? e.employeeCode() : e.employeeNo());
                x.createCell(2).setCellValue(t.getWorkDate().toString());
                x.createCell(3).setCellValue(t.getHours().doubleValue());
                x.createCell(4).setCellValue(t.getProject() == null ? "" : t.getProject());
                x.createCell(5).setCellValue(t.getDescription());
                x.createCell(6).setCellValue(t.getStatus().name());
                x.createCell(7).setCellValue(t.getManagerComments() == null ? "" : t.getManagerComments());
            }
            sh.setColumnWidth(0, 26 * 256);
            sh.setColumnWidth(5, 40 * 256);
            return bytes(wb);
        } catch (IOException ex) {
            throw new IllegalStateException("export", ex);
        }
    }

    private static Employee employee(String v, Map<String, List<Employee>> byName, Map<String, Employee> byCode, int n) {
        if (v == null || v.isBlank()) {
            throw rowError(n, "employee");
        }
        List<Employee> named = byName.getOrDefault(v.trim(), List.of());
        if (named.size() > 1) {
            throw ApiException.badRequest(EAttendanceError.ATT_033, n, v.trim());
        }
        if (named.size() == 1) {
            return named.get(0);
        }
        Employee e = byCode.get(v.trim().toLowerCase(Locale.ROOT));
        if (e == null) {
            throw rowError(n, "employee");
        }
        return e;
    }

    private static LocalDate date(String v, int n) {
        try {
            return LocalDate.parse(v == null ? "" : v.trim());
        } catch (DateTimeParseException e) {
            throw rowError(n, "date");
        }
    }

    private static BigDecimal hours(String v, int n) {
        try {
            BigDecimal h = new BigDecimal(v == null ? "" : v.trim().replace(',', '.'));
            if (h.compareTo(new BigDecimal("0.25")) < 0 || h.compareTo(TimesheetService.DAY_MAX) > 0 || h.scale() > 2) {
                throw rowError(n, "hours");
            }
            return h;
        } catch (NumberFormatException e) {
            throw rowError(n, "hours");
        }
    }

    /** Rows after the header as 5 texts each: .xlsx when the file is a workbook, else CSV (UTF-8, comma). */
    static List<String[]> read(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            List<String[]> rows = bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K' ? xlsx(bytes) : csv(bytes);
            if (rows.size() > MAX_ROWS) {
                throw ApiException.badRequest(EAttendanceError.ATT_014, MAX_ROWS);
            }
            return rows;
        } catch (IOException e) {
            throw ApiException.badRequest(EAttendanceError.ATT_013);
        }
    }

    private static List<String[]> xlsx(byte[] bytes) {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter();
            List<String[]> out = new ArrayList<>();
            for (Row row : wb.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String[] v = new String[5];
                boolean any = false;
                for (int i = 0; i < 5; i++) {
                    Cell c = row.getCell(i);
                    if (c == null) {
                        continue;
                    }
                    if (i == 1 && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                        v[i] = c.getLocalDateTimeCellValue().toLocalDate().toString();
                    } else if (c.getCellType() == CellType.NUMERIC) {
                        v[i] = BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
                    } else {
                        v[i] = fmt.formatCellValue(c).trim();
                    }
                    any |= !v[i].isEmpty();
                }
                if (any) {
                    out.add(v);
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest(EAttendanceError.ATT_013);
        }
    }

    private static List<String[]> csv(byte[] bytes) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line == null || !line.toLowerCase(Locale.ROOT).startsWith("employee")) {
                throw ApiException.badRequest(EAttendanceError.ATT_013);
            }
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = csvFields(line);
                String[] v = new String[5];
                for (int i = 0; i < 5 && i < f.size(); i++) {
                    v[i] = f.get(i).trim();
                }
                out.add(v);
            }
        }
        return out;
    }

    /** One CSV line: comma-separated, "quoted" fields may hold commas and "" for a quote. */
    static List<String> csvFields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static ApiException rowError(int row, String field) {
        return ApiException.badRequest(EAttendanceError.ATT_015, row, field);
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
