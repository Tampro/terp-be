package com.terp.erp.accounting.note;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.client.CommerceClient;
import com.terp.erp.accounting.client.CommerceClient.ESide;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerService;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.note.NoteService.NoteForm;
import com.terp.erp.accounting.vendor.VendorService;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final AccountNoteRepository notes = mock(AccountNoteRepository.class);
    private final CommerceClient commerce = mock(CommerceClient.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final VendorService vendors = mock(VendorService.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final JournalService journal = mock(JournalService.class);
    private final NoteService service = new NoteService(notes, commerce, customers, vendors, numbering, mapping,
            journal, clock);

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setId(5L);
        c.setCompanyId(10L);
        c.setCompanyName("Test Buyer");
        when(customers.require(5L)).thenReturn(c);
        when(numbering.next(anyLong(), anyString(), anyString())).thenAnswer(i -> i.getArgument(1) + "-2026-11-001");
        when(notes.save(any(AccountNote.class))).thenAnswer(i -> {
            AccountNote n = i.getArgument(0);
            n.setId(3L);
            return n;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void salesReturn(String status) {
        when(commerce.returnDoc(ESide.SALES, 7L)).thenReturn(new CommerceClient.ReturnDoc(7L, "SR-2026-11-001", 9L,
                5L, "Test Buyer", DAY, new BigDecimal("110"), new BigDecimal("10"), status));
    }

    private AccountNote draft(Long company) {
        AccountNote n = new AccountNote();
        n.setId(3L);
        n.setCompanyId(company);
        n.setKind(ENoteKind.CREDIT);
        n.setNumber("CN-2026-11-001");
        n.setReturnId(7L);
        n.setReturnKey(7L);
        n.setPartyId(5L);
        n.setPartyName("Test Buyer");
        n.setNoteDate(DAY);
        n.setTotal(new BigDecimal("110"));
        n.setTaxAmount(new BigDecimal("10"));
        n.setBalance(new BigDecimal("110"));
        when(notes.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(n));
        when(notes.lockById(3L)).thenReturn(Optional.of(n));
        return n;
    }

    private static void fails(Runnable call, HttpStatus status, EAccountingError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(code);
            assertThat(((ApiException) e).getStatus()).isEqualTo(status);
        });
    }

    @Test
    void noteFromAReturnNotCompletedIsRefused() {
        TestLogin.as(10L, "NOTE_MANAGE");
        salesReturn("APPROVED");

        fails(() -> service.create(ENoteKind.CREDIT, null, new NoteForm(7L, null, null)), HttpStatus.BAD_REQUEST,
                EAccountingError.ACC_063);
        verify(notes, never()).save(any());
    }

    @Test
    void secondNoteForTheSameReturnIsRefused() {
        TestLogin.as(10L, "NOTE_MANAGE");
        salesReturn("COMPLETED");
        AccountNote existing = draft(10L);
        when(notes.findByCompanyIdAndKindAndReturnKey(10L, ENoteKind.CREDIT, 7L)).thenReturn(Optional.of(existing));

        fails(() -> service.create(ENoteKind.CREDIT, null, new NoteForm(7L, null, null)), HttpStatus.BAD_REQUEST,
                EAccountingError.ACC_064);
    }

    @Test
    void noteFromACompletedReturnTakesItsTotalAndTax() {
        TestLogin.as(10L, "NOTE_MANAGE");
        salesReturn("COMPLETED");
        when(notes.findByCompanyIdAndKindAndReturnKey(10L, ENoteKind.CREDIT, 7L)).thenReturn(Optional.empty());

        NoteService.NoteDto dto = service.create(ENoteKind.CREDIT, null, new NoteForm(7L, null, null));

        assertThat(dto.number()).isEqualTo("CN-2026-11-001");
        assertThat(dto.status()).isEqualTo(ENoteStatus.DRAFT);
        assertThat(dto.total()).isEqualByComparingTo("110");
        assertThat(dto.balance()).isEqualByComparingTo("110");
        assertThat(dto.taxAmount()).isEqualByComparingTo("10");
        assertThat(dto.noteDate()).isEqualTo(DAY);
    }

    @Test
    void approvePostsABalancedCreditNoteEntry() {
        TestLogin.as(10L, "NOTE_APPROVE");
        AccountNote n = draft(10L);
        when(mapping.require(10L, ESystemAccount.SALES_REVENUE)).thenReturn(5111L);
        when(mapping.require(10L, ESystemAccount.OUTPUT_VAT)).thenReturn(33311L);
        when(mapping.require(10L, ESystemAccount.RECEIVABLE)).thenReturn(131L);

        service.approve(ENoteKind.CREDIT, 3L);

        assertThat(n.getStatus()).isEqualTo(ENoteStatus.APPROVED);
        assertThat(n.getApprovedBy()).isEqualTo("kc-5");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalService.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("CREDIT_NOTE"), eq(3L), eq("CN-2026-11-001"), anyString(),
                lines.capture());
        List<JournalService.Line> l = lines.getValue();
        assertThat(l).extracting(JournalService.Line::accountId).containsExactly(5111L, 33311L, 131L);
        assertThat(l.get(0).debit()).isEqualByComparingTo("100");
        assertThat(l.get(1).debit()).isEqualByComparingTo("10");
        assertThat(l.get(2).credit()).isEqualByComparingTo("110");
    }

    @Test
    void onlyADraftCanBeApprovedOrDeleted() {
        TestLogin.as(10L, "NOTE_APPROVE", "NOTE_MANAGE");
        draft(10L).setStatus(ENoteStatus.APPROVED);

        fails(() -> service.approve(ENoteKind.CREDIT, 3L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_062);
        fails(() -> service.delete(ENoteKind.CREDIT, 3L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_062);
    }

    @Test
    void deletingADraftFreesTheReturn() {
        TestLogin.as(10L, "NOTE_MANAGE");
        AccountNote n = draft(10L);

        service.delete(ENoteKind.CREDIT, 3L);

        assertThat(n.isDeleted()).isTrue();
        assertThat(n.getReturnKey()).isNull();
    }

    @Test
    void anotherCompanysNoteIsNotFound() {
        TestLogin.as(11L, "NOTE_VIEW", "NOTE_APPROVE");
        draft(10L);

        fails(() -> service.get(ENoteKind.CREDIT, 3L), HttpStatus.NOT_FOUND, EAccountingError.ACC_061);
        fails(() -> service.approve(ENoteKind.CREDIT, 3L), HttpStatus.NOT_FOUND, EAccountingError.ACC_061);
        verify(journal, never()).post(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
    }
}
