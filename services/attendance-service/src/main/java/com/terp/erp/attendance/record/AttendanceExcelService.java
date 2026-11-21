package com.terp.erp.attendance.record;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.client.RemoteApiException;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.record.RecordDtos.DayCell;
import com.terp.erp.attendance.record.RecordDtos.EmployeeRow;
import com.terp.erp.attendance.record.RecordDtos.ImportResult;
import com.terp.erp.attendance.record.RecordDtos.RecordForm;
import com.terp.erp.attendance.record.RecordDtos.RowError;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import template, import and month export of the Attendance Records page (PO, 2026-11-19: Excel). An import row
 * updates the record of that employee and date when one exists, otherwise creates it.
 */
@Service
@RequiredArgsConstructor
public class AttendanceExcelService {
    static final int MAX_ROWS = 2000;
    static final String[] HEADERS = {"Employee Code or Email", "Date (yyyy-MM-dd)", "Clock In (HH:mm)",
            "Clock Out (HH:mm)", "Holiday (Y/N)", "Notes"};

    private final AttendanceRecordService service;
    private final AttendanceRecordRepository records;
    private final AttendanceSettingService settings;
    private final EmployeeClient employees;
    private final RestExceptionHandler messages;

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Attendance");
            Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                h.createCell(i).setCellValue(HEADERS[i]);
                sh.setColumnWidth(i, 22 * 256);
            }
            Row sample = sh.createRow(1);
            String[] values = {"EMP000001", "2026-11-18", "08:05", "17:10", "N", "Sample row, replace it"};
            for (int i = 0; i < values.length; i++) {
                sample.createCell(i).setCellValue(values[i]);
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("template", e);
        }
    }

    @Transactional
    public ImportResult importFile(Long requestedCompanyId, MultipartFile file) {
        Long companyId = AttendanceRecordService.company(requestedCompanyId);
        List<Row> rows = rows(file);
        Map<String, Employee> byKey = new HashMap<>();
        for (Employee e : employees.all(requestedCompanyId)) {
            for (String k : new String[]{e.employeeCode(), e.employeeNo(), e.email()}) {
                if (k != null && !k.isBlank()) {
                    byKey.putIfAbsent(k.trim().toLowerCase(Locale.ROOT), e);
                }
            }
        }
        int created = 0;
        int updated = 0;
        List<RowError> errors = new ArrayList<>();
        for (Row row : rows) {
            int n = row.getRowNum() + 1;
            try {
                String key = text(row, 0);
                Employee e = key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
                if (e == null) {
                    throw rowError(n, "employee");
                }
                LocalDate date = date(row, n);
                RecordForm form = new RecordForm(Long.valueOf(e.id()), date, time(row, 2, n, "clock in"),
                        time(row, 3, n, "clock out"), "Y".equalsIgnoreCase(text(row, 4)), text(row, 5));
                AttendanceRecord existing = records.findByEmployeeIdAndWorkDate(Long.valueOf(e.id()), date)
                        .filter(r -> r.getCompanyId().equals(companyId)).orElse(null);
                service.save(existing == null ? new AttendanceRecord() : existing, companyId, e, form);
                if (existing == null) {
                    created++;
                } else {
                    updated++;
                }
            } catch (ApiException ex) {
                errors.add(new RowError(n, ex.getErrorCode().getCode(), messages.resolve(ex.getErrorCode(), ex.getArgs())));
            } catch (RemoteApiException ex) {
                errors.add(new RowError(n, ex.getCode(), ex.getRemoteMessage()));
            }
        }
        return new ImportResult(rows.size(), created, updated, errors);
    }

    /** The month grid of every employee: one row per employee, one column per day, a code per cell. */
    @Transactional(readOnly = true)
    public byte[] export(Long requestedCompanyId, int year, int month, Long employeeId) {
        Long companyId = AttendanceRecordService.company(requestedCompanyId);
        YearMonth ym = YearMonth.of(year, month);
        List<Employee> list = employeeId != null ? List.of(employees.get(employeeId)) : employees.all(requestedCompanyId);
        Set<DayOfWeek> off = settings.offDays(companyId);
        List<Holiday> holidays = employees.holidays(requestedCompanyId, ym.atDay(1), ym.atEndOfMonth());
        Map<Long, Map<LocalDate, AttendanceRecord>> byEmployee = list.isEmpty() ? Map.of()
                : records.findByCompanyIdAndEmployeeIdInAndWorkDateBetween(companyId,
                        list.stream().map(e -> Long.valueOf(e.id())).toList(), ym.atDay(1), ym.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getEmployeeId,
                        Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity())));
        LocalDate today = service.today();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet(ym.toString());
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("Employee");
            h.createCell(1).setCellValue("Code");
            h.createCell(2).setCellValue("Designation");
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                h.createCell(2 + d).setCellValue(d);
            }
            int last = 3 + ym.lengthOfMonth();
            h.createCell(last).setCellValue("Attended");
            h.createCell(last + 1).setCellValue("Working days");
            int r = 1;
            for (Employee e : list) {
                EmployeeRow row = AttendanceRecordService.row(e, ym, off, holidays,
                        byEmployee.getOrDefault(Long.valueOf(e.id()), Map.of()), today);
                Row x = sh.createRow(r++);
                x.createCell(0).setCellValue(row.fullName());
                x.createCell(1).setCellValue(row.employeeCode() == null ? "" : row.employeeCode());
                x.createCell(2).setCellValue(row.designation() == null ? "" : row.designation());
                for (int i = 0; i < row.days().size(); i++) {
                    x.createCell(3 + i).setCellValue(code(row.days().get(i)));
                }
                x.createCell(last).setCellValue(row.attended().doubleValue());
                x.createCell(last + 1).setCellValue(row.workingDays());
            }
            Row legend = sh.createRow(r + 1);
            legend.createCell(0).setCellValue("P present, A absent, HD half day, LV on leave, H holiday, OFF day off, "
                    + "- not added; +L late, +E early departure, +OT overtime");
            sh.setColumnWidth(0, 28 * 256);
            return bytes(wb);
        } catch (IOException ex) {
            throw new IllegalStateException("export", ex);
        }
    }

    static String code(DayCell c) {
        String base = switch (c.status()) {
            case PRESENT -> "P";
            case ABSENT -> "A";
            case HALF_DAY -> "HD";
            case ON_LEAVE -> "LV";
            case HOLIDAY -> "H";
            case DAY_OFF -> "OFF";
            case FUTURE -> "";
            case NOT_ADDED -> "-";
        };
        return base + (c.late() ? "+L" : "") + (c.earlyDeparture() ? "+E" : "") + (c.overtime() ? "+OT" : "");
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
                    throw ApiException.badRequest(EAttendanceError.ATT_014, MAX_ROWS);
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) {
                throw a;
            }
            throw ApiException.badRequest(EAttendanceError.ATT_013);
        }
    }

    private static boolean blank(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (text(row, i) != null) {
                return false;
            }
        }
        return true;
    }

    private static String text(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        String s = switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(c) ? null : String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> c.getBooleanCellValue() ? "Y" : "N";
            default -> null;
        };
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate date(Row row, int n) {
        Cell c = row.getCell(1);
        try {
            if (c != null && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = text(row, 1);
            if (s != null) {
                return LocalDate.parse(s);
            }
        } catch (DateTimeParseException ignored) {
            // reported below
        }
        throw rowError(n, "date");
    }

    private static LocalTime time(Row row, int i, int n, String field) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                // A time cell is a fraction of a day.
                long seconds = Math.round(c.getNumericCellValue() % 1 * 86_400);
                return LocalTime.ofSecondOfDay(seconds % 86_400).withSecond(0);
            }
            String s = text(row, i);
            return s == null ? null : LocalTime.parse(s.length() == 4 ? "0" + s : s);
        } catch (DateTimeParseException | IllegalStateException e) {
            throw rowError(n, field);
        }
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
