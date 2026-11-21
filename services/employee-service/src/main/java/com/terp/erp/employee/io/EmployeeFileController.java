package com.terp.erp.employee.io;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.employee.certificate.LetterService;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.EmployeeDtos.Filter;
import com.terp.erp.employee.io.EmployeeExcelService.ImportResult;

import java.nio.charset.StandardCharsets;

@Tag(name = "Employees")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/employees")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeFileController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EmployeeExcelService excel;
    private final LetterService letters;

    @Operation(summary = "Export the employee list (.xlsx)",
            description = "Same filters as the list. Banking data is not exported.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('EMPLOYEE_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) EEmployeeStatus status) {
        byte[] body = excel.export(companyId, new Filter(keyword, branchId, departmentId, designationId, status));
        return file(body, XLSX, "employees.xlsx");
    }

    @Operation(summary = "Import template (.xlsx) with the expected columns and one sample row")
    @PreAuthorize("@PermissionChecker.has('EMPLOYEE_CREATE')")
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return file(excel.template(), XLSX, "employees-import-template.xlsx");
    }

    @Operation(summary = "Import employees from .xlsx (multipart field `file`)",
            description = "First sheet, row 1 = headers of the template, at most 200 rows. Branch, department and "
                    + "designation are matched by name. Each row is created like Add Employee (account + emailed "
                    + "password); refused rows are listed in errors with their row number and the others are kept.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not an .xlsx (EMP_016), too many rows (EMP_017), "
            + "no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('EMPLOYEE_CREATE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<ImportResult> importFile(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(excel.importFile(companyId, file));
    }

    @Operation(summary = "Letter of the employee as PDF",
            description = "type: JOINING_LETTER, EXPERIENCE_CERTIFICATE, NOC. lang: vi (default) or en; ko is "
                    + "served in en for now (no Korean font bundled).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown type (EMP_025)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('EMPLOYEE_VIEW')")
    @GetMapping("/{id}/letters/{type}")
    public ResponseEntity<byte[]> letter(@PathVariable Long id, @PathVariable String type,
                                         @RequestParam(required = false) String lang) {
        LetterService.Letter l = letters.render(id, type, lang);
        return file(l.pdf(), MediaType.APPLICATION_PDF, l.fileName());
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
