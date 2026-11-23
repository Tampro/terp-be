package com.terp.erp.accounting.portal;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.report.AccountDashboardService;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Client / vendor portal dashboard figures, of the party linked to the caller only (never a party from the request). */
@Service
@RequiredArgsConstructor
public class PortalSummaryService {
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final PaymentRepository payments;
    private final AccountNoteRepository notes;
    private final Clock clock;

    public record MonthAmount(int month, BigDecimal amount) {
    }

    @Schema(description = "totalSettled = every CLEARED payment ever; monthly = cleared in the year; notes = credit "
            + "notes (client) / debit notes (vendor) except drafts, all time")
    public record PortalSummary(int year, BigDecimal totalSettled, List<MonthAmount> monthly, BigDecimal notesTotal,
                                long notesCount) {
    }

    @Transactional(readOnly = true)
    public PortalSummary client(Integer year) {
        ErpPrincipal p = linkedPrincipal();
        Customer c = customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return summary(c.getCompanyId(), c.getId(), EPaymentKind.CUSTOMER, ENoteKind.CREDIT, year);
    }

    @Transactional(readOnly = true)
    public PortalSummary vendor(Integer year) {
        ErpPrincipal p = linkedPrincipal();
        Vendor v = vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return summary(v.getCompanyId(), v.getId(), EPaymentKind.VENDOR, ENoteKind.DEBIT, year);
    }

    private PortalSummary summary(Long company, Long party, EPaymentKind kind, ENoteKind noteKind, Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        Map<Integer, BigDecimal> m = AccountDashboardService.byMonth(payments.clearedByMonth(company, kind, party,
                LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)));
        List<MonthAmount> monthly = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            monthly.add(new MonthAmount(i, m.getOrDefault(i, BigDecimal.ZERO)));
        }
        BigDecimal settled = payments.sumCleared(company, kind, party);
        List<Object[]> n = notes.issuedSummary(company, noteKind, party);
        Object[] row = n.isEmpty() ? new Object[]{BigDecimal.ZERO, 0L} : n.get(0);
        return new PortalSummary(y, settled == null ? BigDecimal.ZERO : settled, monthly,
                row[0] == null ? BigDecimal.ZERO : (BigDecimal) row[0],
                row[1] == null ? 0 : ((Number) row[1]).longValue());
    }

    // A super admin has no company / user id, so is linked to nothing.
    private static ErpPrincipal linkedPrincipal() {
        ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            throw ApiException.notFound(EAccountingError.ACC_098);
        }
        return p;
    }
}
