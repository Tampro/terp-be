package com.terp.erp.accounting.posting;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalEntry;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.posting.DocumentPostingService.ESource;
import com.terp.erp.accounting.posting.DocumentPostingService.PostingForm;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentPostingServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);
    private final JournalService journal = mock(JournalService.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final DocumentPostingService service = new DocumentPostingService(journal, entries, mapping);

    @BeforeEach
    void setUp() {
        for (ESystemAccount k : ESystemAccount.values()) {
            when(mapping.require(10L, k)).thenReturn((long) k.ordinal() + 100);
        }
        when(mapping.find(10L, ESystemAccount.SERVICE_REVENUE))
                .thenReturn(Optional.of((long) ESystemAccount.SERVICE_REVENUE.ordinal() + 100));
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(any(), any(), any())).thenReturn(Optional.empty());
        when(journal.post(eq(10L), any(), any(), any(), any(), any(), anyList()))
                .thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null, null, null,
                        null, null, null, null, null, List.of(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static long acc(ESystemAccount k) {
        return k.ordinal() + 100;
    }

    private static PostingForm form(ESource s, String goods, String service, String tax, String cost, boolean v) {
        return new PostingForm(s, 40L, DAY, "DOC-1", "Doc 1", goods == null ? null : new BigDecimal(goods),
                service == null ? null : new BigDecimal(service), tax == null ? null : new BigDecimal(tax),
                cost == null ? null : new BigDecimal(cost), v);
    }

    @SuppressWarnings("unchecked")
    private List<JournalService.Line> posted(String sourceType) {
        ArgumentCaptor<List<JournalService.Line>> c = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq(sourceType), eq(40L), eq("DOC-1"), eq("Doc 1"), c.capture());
        List<JournalService.Line> l = c.getValue();
        BigDecimal dr = l.stream().map(x -> x.debit() == null ? BigDecimal.ZERO : x.debit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = l.stream().map(x -> x.credit() == null ? BigDecimal.ZERO : x.credit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dr).isEqualByComparingTo(cr);
        return l;
    }

    private static void line(JournalService.Line l, ESystemAccount k, String dr, String cr) {
        assertThat(l.accountId()).isEqualTo(acc(k));
        if (dr != null) {
            assertThat(l.debit()).isEqualByComparingTo(dr);
        } else {
            assertThat(l.credit()).isEqualByComparingTo(cr);
        }
    }

    @Test
    void salesInvoiceDebitsReceivableCreditsRevenueByKindAndVatAndBooksCost() {
        TestLogin.as(10L, "SALES_POST");
        service.post(null, form(ESource.SALES_INVOICE, "200", "100", "20", "120", false));
        List<JournalService.Line> l = posted("SALES_INVOICE");
        assertThat(l).hasSize(6);
        line(l.get(0), ESystemAccount.RECEIVABLE, "320", null);
        line(l.get(1), ESystemAccount.SALES_REVENUE, null, "200");
        line(l.get(2), ESystemAccount.SERVICE_REVENUE, null, "100");
        line(l.get(3), ESystemAccount.OUTPUT_VAT, null, "20");
        line(l.get(4), ESystemAccount.COGS, "120", null);
        line(l.get(5), ESystemAccount.INVENTORY, null, "120");
    }

    @Test
    void serviceRevenueFallsBackToSalesRevenueWhenUnmapped() {
        TestLogin.as(10L, "SALES_POST");
        when(mapping.find(10L, ESystemAccount.SERVICE_REVENUE)).thenReturn(Optional.empty());
        service.post(null, form(ESource.SALES_INVOICE, null, "100", null, null, false));
        List<JournalService.Line> l = posted("SALES_INVOICE");
        line(l.get(1), ESystemAccount.SALES_REVENUE, null, "100");
    }

    @Test
    void salesReturnReversesRevenueVatAndCost() {
        TestLogin.as(10L, "SALES_POST");
        service.post(null, form(ESource.SALES_RETURN, "180", null, "18", "120", false));
        List<JournalService.Line> l = posted("SALES_RETURN");
        assertThat(l).hasSize(5);
        line(l.get(0), ESystemAccount.SALES_RETURNS, "180", null);
        line(l.get(1), ESystemAccount.OUTPUT_VAT, "18", null);
        line(l.get(2), ESystemAccount.RECEIVABLE, null, "198");
        line(l.get(3), ESystemAccount.INVENTORY, "120", null);
        line(l.get(4), ESystemAccount.COGS, null, "120");
    }

    @Test
    void purchaseInvoiceAndReturn() {
        TestLogin.as(10L, "PURCHASE_POST");
        service.post(null, form(ESource.PURCHASE_INVOICE, "300", "20", "30", null, false));
        List<JournalService.Line> l = posted("PURCHASE_INVOICE");
        line(l.get(0), ESystemAccount.INVENTORY, "300", null);
        line(l.get(1), ESystemAccount.GENERAL_EXPENSE, "20", null);
        line(l.get(2), ESystemAccount.INPUT_VAT, "30", null);
        line(l.get(3), ESystemAccount.PAYABLE, null, "350");

        service.post(null, form(ESource.PURCHASE_RETURN, "120", null, "12", null, false));
        List<JournalService.Line> r = posted("PURCHASE_RETURN");
        assertThat(r).hasSize(3);
        line(r.get(0), ESystemAccount.PAYABLE, "132", null);
        line(r.get(1), ESystemAccount.INVENTORY, null, "120");
        line(r.get(2), ESystemAccount.INPUT_VAT, null, "12");
    }

    @Test
    void aRepeatReturnsTheFirstEntryAndPostsNothing() {
        TestLogin.as(10L, "SALES_POST");
        JournalEntry e = new JournalEntry();
        e.setId(50L);
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 40L)).thenReturn(Optional.of(e));
        when(journal.get(50L)).thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null,
                null, null, "SALES_INVOICE", "40", null, null, null, List.of(), null));
        var dto = service.post(null, form(ESource.SALES_INVOICE, "200", null, "20", null, false));
        assertThat(dto.journalNumber()).isEqualTo("JE-2026-001");
        verify(journal, never()).post(any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void validateOnlyChecksTheClosedYearAndWritesNothing() {
        TestLogin.as(10L, "SALES_POST");
        var dto = service.post(null, form(ESource.SALES_INVOICE, "200", null, "20", null, true));
        assertThat(dto.validateOnly()).isTrue();
        verify(journal).assertOpen(10L, DAY);
        verify(journal, never()).post(any(), any(), any(), any(), any(), any(), anyList());

        doThrow(ApiException.badRequest(EAccountingError.ACC_070, "2026-12-31")).when(journal).assertOpen(10L, DAY);
        assertThatThrownBy(() -> service.post(null, form(ESource.SALES_INVOICE, "200", null, "20", null, true)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EAccountingError.ACC_070);
    }

    @Test
    void shopOrderDebitsPgClearingAndIsRefusedToStaffOverRest() {
        // G = 10,000 incl. VAT → 9,091 + 909; fees F = 3,200 → 2,909 + 291; cost 5,000
        service.postFor(10L, form(ESource.SHOP_ORDER, "9091", "2909", "1200", "5000", false));
        List<JournalService.Line> l = posted("SHOP_ORDER");
        assertThat(l).hasSize(6);
        line(l.get(0), ESystemAccount.PG_CLEARING, "13200", null);
        line(l.get(1), ESystemAccount.SALES_REVENUE, null, "9091");
        line(l.get(2), ESystemAccount.SERVICE_REVENUE, null, "2909");
        line(l.get(3), ESystemAccount.OUTPUT_VAT, null, "1200");
        line(l.get(4), ESystemAccount.COGS, "5000", null);
        line(l.get(5), ESystemAccount.INVENTORY, null, "5000");

        TestLogin.as(10L, "SALES_POST", "PURCHASE_POST");
        assertThatThrownBy(() -> service.post(null, form(ESource.SHOP_ORDER, "1", null, null, null, false)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommonError.COMMON_004);
    }

    @Test
    void eachSourceNeedsItsOwnPermissionAndSomeAmount() {
        TestLogin.as(10L, "SALES_POST");
        assertThatThrownBy(() -> service.post(null, form(ESource.PURCHASE_INVOICE, "1", null, null, null, false)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommonError.COMMON_004);
        assertThatThrownBy(() -> service.post(null, form(ESource.SALES_INVOICE, "0", null, null, null, false)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EAccountingError.ACC_019);
    }
}
