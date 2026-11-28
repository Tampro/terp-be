package com.terp.erp.order.order;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * HE-yyyyMMdd-NNNNNN per company per day (store local date). The row of (company, day) is created if missing and
 * incremented under its lock, so two checkouts never get the same number (uk_order_number is the last guard).
 */
@Component
@RequiredArgsConstructor
public class OrderNumbers {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(long companyId, LocalDate day) {
        jdbc.update("INSERT INTO order_sequence (company_id, day, last_no) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE last_no = last_no + 1", companyId, day);
        Integer no = jdbc.queryForObject("SELECT last_no FROM order_sequence WHERE company_id = ? AND day = ?",
                Integer.class, companyId, day);
        return format(day, no == null ? 1 : no);
    }

    static String format(LocalDate day, int no) {
        return "HE-" + day.format(DAY) + "-" + String.format("%06d", no);
    }
}
