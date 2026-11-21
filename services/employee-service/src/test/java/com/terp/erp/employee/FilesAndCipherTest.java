package com.terp.erp.employee;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.common.FieldCipher;
import com.terp.erp.employee.config.EmployeeProperties;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesAndCipherTest {

    @Test
    void cipherRoundTripsAndNeverStoresClearText() {
        FieldCipher c = cipher();
        String stored = c.encrypt("0123456789");
        assertThat(stored).doesNotContain("0123456789");
        assertThat(c.encrypt("0123456789")).isNotEqualTo(stored); // random IV
        assertThat(c.decrypt(stored)).isEqualTo("0123456789");
        assertThat(c.encrypt("  ")).isNull();
        assertThat(FieldCipher.mask("0123456789")).isEqualTo("******6789");
    }

    @Test
    void valueEncryptedWithAnotherKeyReadsAsMissing() {
        byte[] other = new byte[32];
        other[0] = 1;
        String stored = new FieldCipher(new EmployeeProperties("i", "m", Base64.getEncoder().encodeToString(other)))
                .encrypt("x");
        assertThat(cipher().decrypt(stored)).isNull();
    }

    @Test
    void badKeyStopsStartupWithoutPrintingIt() {
        assertThatThrownBy(() -> new FieldCipher(new EmployeeProperties("i", "m", "c2hvcnQ=")))
                .hasMessageContaining("32 bytes").hasMessageNotContaining("c2hvcnQ=");
        assertThatThrownBy(() -> new FieldCipher(new EmployeeProperties("i", "m", "not base64!")))
                .hasMessageContaining("not valid base64").hasMessageNotContaining("not base64!");
    }

    @Test
    void letterIsAPdfWithTheValuesFilledIn() {
        byte[] pdf = invokePdf(new String[]{"THƯ MỜI NHẬN VIỆC", "Ngày {today}", "Kính gửi {name}", "Đại diện {company}"},
                Map.of("company", "Công ty Demo", "name", "Nguyễn Văn A", "today", "01/12/2026"));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void importRowIsMappedByNamesAndRefusedWhenUnknown() throws Exception {
        Branch b = new Branch();
        b.setId(1L);
        b.setName("Head office");
        Department d = new Department();
        d.setId(2L);
        d.setBranchId(1L);
        d.setName("IT");
        OrgService.Tree tree = new OrgService.Tree(List.of(b), List.of(d), List.of());
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet();
            Row r = s.createRow(1);
            String[] v = {"Nguyen Van A", "a@example.com", "", "", "1995-01-31", "male", "head office", "it", "",
                    "2026-12-01", "FULL_TIME", "probation"};
            for (int i = 0; i < v.length; i++) {
                r.createCell(i).setCellValue(v[i]);
            }
            var form = com.terp.erp.employee.io.ImportRowsAccess.toForm(r, tree, 2);
            assertThat(form.profile().branchId()).isEqualTo(1L);
            assertThat(form.profile().departmentId()).isEqualTo(2L);
            assertThat(form.profile().dateOfBirth()).isEqualTo(LocalDate.of(1995, 1, 31));
            assertThat(form.profile().status()).isEqualTo(EEmployeeStatus.PROBATION);

            r.getCell(7).setCellValue("Sales");
            assertThatThrownBy(() -> com.terp.erp.employee.io.ImportRowsAccess.toForm(r, tree, 2))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EEmployeeError.EMP_022);
        }
    }

    private static byte[] invokePdf(String[] text, Map<String, String> v) {
        return com.terp.erp.employee.certificate.LetterAccess.pdf(text, v);
    }

    private static FieldCipher cipher() {
        return new FieldCipher(new EmployeeProperties("i", "m", Base64.getEncoder().encodeToString(new byte[32])));
    }
}
