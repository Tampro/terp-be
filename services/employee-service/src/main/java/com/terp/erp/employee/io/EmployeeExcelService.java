package com.terp.erp.employee.io;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.employee.client.RemoteApiException;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.EEmploymentType;
import com.terp.erp.employee.employee.EGender;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeCreateForm;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeListItem;
import com.terp.erp.employee.employee.EmployeeDtos.Filter;
import com.terp.erp.employee.employee.EmployeeDtos.ProfileForm;
import com.terp.erp.employee.employee.EmployeeService;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.Designation;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Export of the employee list and bulk import (docs/analysis/06 §6.1 Export / Import), as .xlsx.
 * Import: one row = one employee created exactly like "Add Employee" (own transaction, IAM account, emailed
 * password). Rows are independent: a refused row is reported and the others are still created.
 * Banking data is neither exported nor imported.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeExcelService {
    static final int MAX_ROWS = 200;
    static final String[] IMPORT_HEADERS = {"Full name*", "Email*", "Employee code", "Phone", "Date of birth (yyyy-mm-dd)",
            "Gender (MALE/FEMALE/OTHER)", "Branch", "Department", "Designation", "Date of joining* (yyyy-mm-dd)",
            "Employment type* (FULL_TIME/PART_TIME/CONTRACT/INTERN/TEMPORARY)",
            "Status* (ACTIVE/INACTIVE/PROBATION/TERMINATED)"};
    static final String[] EXPORT_HEADERS = {"Employee ID", "Full name", "Email", "Employee code", "Branch", "Department",
            "Designation", "Employee status", "Employment type", "Joined", "Login status"};

    public record RowError(int row, String code, String message) {
    }

    public record ImportResult(int total, int created, List<RowError> errors) {
    }

    private final EmployeeService employees;
    private final OrgService orgs;
    private final Validator validator;
    private final RestExceptionHandler messages;

    public byte[] export(Long requestedCompanyId, Filter filter) {
        Long companyId = EmployeeService.company(requestedCompanyId);
        List<EmployeeListItem> rows = employees.searchAll(companyId, filter);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Employees");
            header(wb, sheet, EXPORT_HEADERS);
            int r = 1;
            for (EmployeeListItem e : rows) {
                Row row = sheet.createRow(r++);
                String[] v = {e.employeeNo(), e.fullName(), e.email(), e.employeeCode(), name(e.branch()),
                        name(e.department()), name(e.designation()), e.status().name(), e.employmentType().name(),
                        e.dateOfJoining() == null ? null : e.dateOfJoining().toString(),
                        e.loginEnabled() ? "ACTIVE" : "INACTIVE"};
                for (int c = 0; c < v.length; c++) {
                    row.createCell(c).setCellValue(v[c] == null ? "" : v[c]);
                }
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("xlsx export failed", e);
        }
    }

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Employees");
            header(wb, sheet, IMPORT_HEADERS);
            Row sample = sheet.createRow(1);
            String[] v = {"Nguyen Van A", "a.nguyen@example.com", "EMP001", "0900000000", "1995-01-31", "MALE",
                    "Head office", "Information Technology", "Developer", "2026-12-01", "FULL_TIME", "PROBATION"};
            for (int c = 0; c < v.length; c++) {
                sample.createCell(c).setCellValue(v[c]);
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("xlsx template failed", e);
        }
    }

    /** Not transactional on purpose: each row commits (or fails) on its own through EmployeeService.create. */
    public ImportResult importFile(Long requestedCompanyId, MultipartFile file) {
        Long companyId = EmployeeService.company(requestedCompanyId);
        List<Row> rows = readRows(file);
        OrgService.Tree tree = orgs.tree(companyId);
        List<RowError> errors = new ArrayList<>();
        int created = 0;
        for (Row row : rows) {
            int excelRow = row.getRowNum() + 1;
            try {
                EmployeeCreateForm form = toForm(row, tree, excelRow);
                Set<ConstraintViolation<EmployeeCreateForm>> invalid = validator.validate(form);
                if (!invalid.isEmpty()) {
                    String field = invalid.iterator().next().getPropertyPath().toString();
                    throw ApiException.badRequest(EEmployeeError.EMP_022, excelRow, field);
                }
                employees.create(companyId, form);
                created++;
            } catch (ApiException e) {
                errors.add(new RowError(excelRow, e.getErrorCode().getCode(), messages.resolve(e.getErrorCode(), e.getArgs())));
            } catch (RemoteApiException e) {
                errors.add(new RowError(excelRow, e.getCode(), e.getRemoteMessage()));
            }
        }
        return new ImportResult(rows.size(), created, errors);
    }

    // ------------------------------------------------------------------ parsing

    private List<Row> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(EEmployeeError.EMP_016);
        }
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            List<Row> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && !blank(row)) {
                    rows.add(row);
                }
            }
            if (rows.size() > MAX_ROWS) {
                throw ApiException.badRequest(EEmployeeError.EMP_017, MAX_ROWS);
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            // Not a zip / not OOXML / corrupt: the client's file, not a server error.
            throw ApiException.badRequest(EEmployeeError.EMP_016);
        }
    }

    static EmployeeCreateForm toForm(Row row, OrgService.Tree tree, int excelRow) {
        Long branchId = null;
        Long departmentId = null;
        Long designationId = null;
        String branch = text(row, 6);
        String department = text(row, 7);
        String designation = text(row, 8);
        if (branch != null) {
            branchId = tree.branches().stream().filter(b -> b.getName().equalsIgnoreCase(branch)).map(Branch::getId)
                    .findFirst().orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_022, excelRow, "branch"));
        }
        if (department != null) {
            Long b = branchId;
            departmentId = tree.departments().stream()
                    .filter(d -> Objects.equals(d.getBranchId(), b) && d.getName().equalsIgnoreCase(department))
                    .map(Department::getId).findFirst()
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_022, excelRow, "department"));
        }
        if (designation != null) {
            Long d = departmentId;
            designationId = tree.designations().stream()
                    .filter(x -> Objects.equals(x.getDepartmentId(), d) && x.getName().equalsIgnoreCase(designation))
                    .map(Designation::getId).findFirst()
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_022, excelRow, "designation"));
        }
        ProfileForm profile = new ProfileForm(text(row, 0), text(row, 2), text(row, 3),
                date(row, 4, excelRow, "dateOfBirth"), enumOf(EGender.class, row, 5, excelRow, "gender"), null,
                branchId, departmentId, designationId, date(row, 9, excelRow, "dateOfJoining"),
                enumOf(EEmploymentType.class, row, 10, excelRow, "employmentType"),
                enumOf(EEmployeeStatus.class, row, 11, excelRow, "status"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        return new EmployeeCreateForm(text(row, 1), null, profile, List.of());
    }

    private static final DataFormatter FORMAT = new DataFormatter(Locale.ROOT);

    static String text(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        String s = FORMAT.formatCellValue(c).trim();
        return s.isEmpty() ? null : s;
    }

    static LocalDate date(Row row, int col, int excelRow, String field) {
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getDateCellValue().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        String s = text(row, col);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(EEmployeeError.EMP_022, excelRow, field);
        }
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, Row row, int col, int excelRow, String field) {
        String s = text(row, col);
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, s.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(EEmployeeError.EMP_022, excelRow, field);
        }
    }

    private static boolean blank(Row row) {
        for (Cell c : row) {
            if (c != null && !FORMAT.formatCellValue(c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ writing

    private static void header(Workbook wb, Sheet sheet, String[] headers) {
        CellStyle style = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        style.setFont(bold);
        Row row = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(style);
            // Fixed widths: autoSizeColumn needs AWT fonts, which the alpine image does not have.
            sheet.setColumnWidth(c, 22 * 256);
        }
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String name(com.terp.erp.employee.employee.EmployeeDtos.Ref ref) {
        return ref == null ? null : ref.name();
    }
}
