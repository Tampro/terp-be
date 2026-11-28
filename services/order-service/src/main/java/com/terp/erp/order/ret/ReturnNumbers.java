package com.terp.erp.order.ret;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** RQ-yyyyMMdd-nnnnnn per company and day (Asia/Seoul day, chosen by the caller), in the caller's transaction. */
@Component
@RequiredArgsConstructor
public class ReturnNumbers {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(long companyId, LocalDate day) {
        jdbc.update("INSERT INTO return_sequence (company_id, day, last_no) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE last_no = last_no + 1", companyId, day);
        Integer no = jdbc.queryForObject("SELECT last_no FROM return_sequence WHERE company_id = ? AND day = ?",
                Integer.class, companyId, day);
        return format(day, no == null ? 1 : no);
    }

    static String format(LocalDate day, int no) {
        return "RQ-" + day.format(DAY) + "-" + String.format("%06d", no);
    }
}
