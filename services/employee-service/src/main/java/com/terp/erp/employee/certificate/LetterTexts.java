package com.terp.erp.employee.certificate;

import java.util.List;
import java.util.Map;

/**
 * Built-in titles and default bodies of the letters, used until a company saves its own (LetterTemplate).
 * TODO(assumption): draft wording, to be replaced by the company's own templates (confirm with HR/BA).
 * ko has no default of its own and no Korean font yet: it shows and prints the en text (PO 2026-11-21).
 */
final class LetterTexts {
    static final List<String> LANGS = List.of("vi", "en", "ko");

    private static final List<String> COMMON = List.of("{date}", "{company_name}", "{employee_name}", "{employee_no}",
            "{designation}", "{department}", "{joining_date}");
    private static final String SIGN_VI = "<p style=\"text-align: right\">Đại diện {company_name}</p>";
    private static final String SIGN_EN = "<p style=\"text-align: right\">On behalf of {company_name}</p>";

    private LetterTexts() {
    }

    /** The language whose text is printed: Korean prints English until a CJK font is bundled. */
    static String printLang(String lang) {
        return "ko".equals(lang) ? "en" : lang;
    }

    /** Spec placeholders of each letter (§10.5.8-10.5.10) plus the employee number and department. */
    static List<String> placeholders(ELetterType type) {
        return switch (type) {
            case NOC -> COMMON;
            case EXPERIENCE_CERTIFICATE -> concat(COMMON, "{leaving_date}");
            case JOINING_LETTER -> concat(COMMON, "{salary}");
        };
    }

    static String title(ELetterType type, String lang) {
        boolean vi = "vi".equals(printLang(lang));
        return switch (type) {
            case JOINING_LETTER -> vi ? "THƯ MỜI NHẬN VIỆC" : "JOINING LETTER";
            case EXPERIENCE_CERTIFICATE -> vi ? "GIẤY XÁC NHẬN KINH NGHIỆM LÀM VIỆC" : "EXPERIENCE CERTIFICATE";
            case NOC -> vi ? "GIẤY XÁC NHẬN KHÔNG PHẢN ĐỐI" : "NO OBJECTION CERTIFICATE";
        };
    }

    static String dateLine(String lang) {
        return "vi".equals(printLang(lang)) ? "Ngày {date}" : "{date}";
    }

    static String body(ELetterType type, String lang) {
        return "vi".equals(printLang(lang)) ? VI.get(type) : EN.get(type);
    }

    private static final Map<ELetterType, String> VI = Map.of(
            ELetterType.JOINING_LETTER, "<p>Kính gửi Ông/Bà {employee_name},</p>"
                    + "<p>{company_name} trân trọng xác nhận Ông/Bà được tiếp nhận vào làm việc ở vị trí "
                    + "{designation}, bộ phận {department}, kể từ ngày {joining_date}, với mức lương cơ bản "
                    + "{salary}.</p>"
                    + "<p>Mã nhân viên của Ông/Bà là {employee_no}. Các điều khoản chi tiết được quy định trong hợp "
                    + "đồng lao động.</p>"
                    + "<p>Chúc Ông/Bà thành công trong công việc mới.</p>" + SIGN_VI,
            ELetterType.EXPERIENCE_CERTIFICATE, "<p>{company_name} xác nhận Ông/Bà {employee_name} (mã nhân viên "
                    + "{employee_no}) đã làm việc tại công ty ở vị trí {designation}, bộ phận {department}, từ ngày "
                    + "{joining_date} đến ngày {leaving_date}.</p>"
                    + "<p>Giấy xác nhận này được cấp theo đề nghị của người lao động.</p>" + SIGN_VI,
            ELetterType.NOC, "<p>{company_name} xác nhận Ông/Bà {employee_name} (mã nhân viên {employee_no}), vị trí "
                    + "{designation}, bộ phận {department}, làm việc tại công ty từ ngày {joining_date}.</p>"
                    + "<p>Công ty không phản đối việc Ông/Bà sử dụng giấy này cho mục đích hợp pháp của mình.</p>"
                    + SIGN_VI);

    private static final Map<ELetterType, String> EN = Map.of(
            ELetterType.JOINING_LETTER, "<p>Dear {employee_name},</p>"
                    + "<p>{company_name} is pleased to confirm your appointment as {designation} in the {department} "
                    + "department, effective {joining_date}, with a base salary of {salary}.</p>"
                    + "<p>Your employee ID is {employee_no}. The detailed terms are set out in your employment "
                    + "contract.</p>"
                    + "<p>We wish you every success in your new role.</p>" + SIGN_EN,
            ELetterType.EXPERIENCE_CERTIFICATE, "<p>This is to certify that {employee_name} (employee ID "
                    + "{employee_no}) has worked with {company_name} as {designation} in the {department} department "
                    + "from {joining_date} to {leaving_date}.</p>"
                    + "<p>This certificate is issued at the request of the employee.</p>" + SIGN_EN,
            ELetterType.NOC, "<p>This is to certify that {employee_name} (employee ID {employee_no}), {designation} "
                    + "in the {department} department, has been employed by {company_name} since {joining_date}.</p>"
                    + "<p>The company has no objection to this certificate being used for any lawful purpose.</p>"
                    + SIGN_EN);

    private static List<String> concat(List<String> a, String b) {
        return java.util.stream.Stream.concat(a.stream(), java.util.stream.Stream.of(b)).toList();
    }
}
