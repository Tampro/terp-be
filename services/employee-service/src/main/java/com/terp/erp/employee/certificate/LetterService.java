package com.terp.erp.employee.certificate;

import com.lowagie.text.pdf.BaseFont;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeService;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.security.CurrentUser;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Joining letter, experience certificate and NOC of docs/analysis/06 §6.1 (Certifications tab), as PDF, from the
 * company's letter template (§10.5.8-10.5.10) or the built-in default.
 */
@Service
@RequiredArgsConstructor
public class LetterService {
    public static final String FONT = LetterPdf.FONT;
    public static final String FONT_BOLD = LetterPdf.FONT_BOLD;

    public record Letter(String fileName, byte[] pdf) {
    }

    private final EmployeeService employees;
    private final OrgService orgs;
    private final IamClient iam;
    private final LetterTemplateService templates;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Letter render(Long employeeId, String typeCode, String lang) {
        ELetterType type = LetterTemplateService.type(typeCode);
        String l = LetterTexts.printLang(LetterTemplateService.lang(lang));
        Employee e = employees.require(employeeId);
        String company = iam.company(e.getCompanyId()).name();
        var names = orgs.names(e.getCompanyId(), ids(e.getBranchId()), ids(e.getDepartmentId()), ids(e.getDesignationId()));
        LocalDate today = LocalDate.now(clock);
        Map<String, String> v = Map.of(
                "company_name", company,
                "employee_name", e.getFullName(),
                "employee_no", e.getEmployeeNo(),
                "designation", names.designations().getOrDefault(e.getDesignationId(), "-"),
                "department", names.departments().getOrDefault(e.getDepartmentId(), "-"),
                "joining_date", date(e.getDateOfJoining(), l),
                // TODO(assumption): no date of leaving yet = the issue date (a certificate for a current employee).
                "leaving_date", date(e.getDateOfLeaving() == null ? today : e.getDateOfLeaving(), l),
                "salary", salary(e.getBaseSalary(), l),
                "date", date(today, l));
        byte[] pdf = LetterPdf.render(company, LetterTexts.dateLine(l), LetterTexts.title(type, l),
                templates.body(e.getCompanyId(), type, l), v);
        return new Letter(type.name().toLowerCase(Locale.ROOT) + "-" + e.getEmployeeNo() + ".pdf", pdf);
    }

    /** Embedded TTF: the image has no system fonts and the built-in PDF fonts have no Vietnamese glyphs. */
    public static BaseFont font(String path) throws IOException {
        return LetterPdf.font(path);
    }

    /** The base salary is banking data: printed only for callers who may see it (EMPLOYEE_BANKING_VIEW). */
    private static String salary(BigDecimal s, String lang) {
        if (s == null || !CurrentUser.get().has(EmployeeService.BANKING_VIEW)) {
            return "-";
        }
        NumberFormat f = NumberFormat.getNumberInstance("en".equals(lang) ? Locale.ENGLISH : Locale.forLanguageTag("vi"));
        f.setMaximumFractionDigits(2);
        return f.format(s);
    }

    private static String date(LocalDate d, String lang) {
        if (d == null) {
            return "-";
        }
        return switch (lang) {
            case "en" -> d.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
            default -> d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        };
    }

    private static List<Long> ids(Long id) {
        return id == null ? List.of() : List.of(id);
    }
}
