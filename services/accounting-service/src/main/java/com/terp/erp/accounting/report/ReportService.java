package com.terp.erp.accounting.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.accounting.client.CommerceClient;
import com.terp.erp.accounting.client.CommerceClient.ESide;
import com.terp.erp.accounting.client.CommerceClient.Invoice;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.customer.CustomerService;
import com.terp.erp.accounting.note.AccountNote;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.Payment;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.accounting.vendor.VendorService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accounting reports (ERP manual 12.6, 12.1; FR-ACC-RPT-001, FR-ACC-CUS-002, FR-ACC-VEN-002). Invoices and returns
 * come from commerce-service with the caller's token (the caller needs SALES_VIEW / PURCHASE_VIEW there); notes and
 * payments are local. Customer / vendor balance is computed, not stored: open invoice balances - unapplied notes.
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    public static final int MAX_DAYS = 366;
    private static final List<String> OPEN = List.of("POSTED", "PARTIAL");
    private static final List<String> ISSUED = List.of("POSTED", "PARTIAL", "PAID");

    public enum EParty { CUSTOMER, VENDOR }

    public record AgingRow(String partyId, String partyName, BigDecimal current, BigDecimal days1To30,
                           BigDecimal days31To60, BigDecimal days61To90, BigDecimal over90, BigDecimal total) {
    }

    public record AgingReport(EParty party, LocalDate asOf, List<AgingRow> rows, AgingRow totals) {
    }

    public record TaxSummary(LocalDate from, LocalDate to, BigDecimal outputTax, BigDecimal creditNoteTax,
                             BigDecimal netOutputTax, BigDecimal inputTax, BigDecimal debitNoteTax,
                             BigDecimal netInputTax, BigDecimal netTaxLiability, int salesInvoices,
                             int purchaseInvoices) {
    }

    public record BalanceRow(String partyId, String code, String name, BigDecimal openInvoices,
                             BigDecimal unappliedNotes, BigDecimal balance) {
    }

    public record DocLine(String id, String number, LocalDate date, BigDecimal amount, String status) {
    }

    public record PartyReport(EParty party, String partyId, String code, String name, LocalDate from, LocalDate to,
                              BigDecimal invoiced, BigDecimal returns, BigDecimal notes, BigDecimal payments,
                              BigDecimal balance, List<DocLine> invoices, List<DocLine> returnDocs,
                              List<DocLine> noteDocs, List<DocLine> paymentDocs) {
    }

    private final CommerceClient commerce;
    private final AccountNoteRepository notes;
    private final PaymentRepository payments;
    private final CustomerService customerService;
    private final VendorService vendorService;
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final Clock clock;

    // ---------- aging ----------

    /**
     * Open (POSTED / PARTIAL) invoices dated on or before asOf, bucketed by days past the due date. TODO(assumption):
     * the balance is today's (payments after asOf are already deducted) — commerce keeps no balance history.
     */
    @Transactional(readOnly = true)
    public AgingReport aging(EParty party, Long requestedCompanyId, LocalDate asOf, Long partyId) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
        if (partyId != null) {
            requireParty(party, partyId, company);
        }
        List<Invoice> open = new ArrayList<>();
        for (String s : OPEN) {
            open.addAll(commerce.invoices(side(party), company, partyId, s, null, date));
        }
        return aging(party, date, open);
    }

    /** Current = not yet due (due date on or after asOf); then 1–30, 31–60, 61–90, over 90 days past due. */
    static AgingReport aging(EParty party, LocalDate asOf, List<Invoice> invoices) {
        Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        BigDecimal[] totals = zeros();
        for (Invoice i : invoices) {
            if (i.balance().signum() == 0) {
                continue;
            }
            long late = i.dueDate() == null ? 0 : ChronoUnit.DAYS.between(i.dueDate(), asOf);
            int b = late <= 0 ? 0 : late <= 30 ? 1 : late <= 60 ? 2 : late <= 90 ? 3 : 4;
            BigDecimal[] row = sums.computeIfAbsent(i.partyId(), k -> zeros());
            names.putIfAbsent(i.partyId(), i.partyName());
            row[b] = row[b].add(i.balance());
            row[5] = row[5].add(i.balance());
            totals[b] = totals[b].add(i.balance());
            totals[5] = totals[5].add(i.balance());
        }
        List<AgingRow> rows = sums.entrySet().stream()
                .map(e -> row(e.getKey().toString(), names.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(r -> r.partyName() == null ? "" : r.partyName()))
                .toList();
        return new AgingReport(party, asOf, rows, row(null, "Total", totals));
    }

    // ---------- tax ----------

    /**
     * Output tax of issued sales invoices and input tax of issued purchase invoices dated in [from, to], less the
     * tax of approved credit / debit notes dated in the period. TODO(assumption): tax per tax type and the VAT
     * declaration rules (VAS, invoice vs payment date) come from the chief accountant.
     */
    @Transactional(readOnly = true)
    public TaxSummary taxSummary(Long requestedCompanyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkPeriod(from, to);
        List<Invoice> sales = issued(ESide.SALES, company, null, from, to);
        List<Invoice> purchases = issued(ESide.PURCHASE, company, null, from, to);
        BigDecimal output = sum(sales.stream().map(Invoice::tax).toList());
        BigDecimal input = sum(purchases.stream().map(Invoice::tax).toList());
        BigDecimal cnTax = noteTax(company, ENoteKind.CREDIT, from, to);
        BigDecimal dnTax = noteTax(company, ENoteKind.DEBIT, from, to);
        BigDecimal netOut = output.subtract(cnTax);
        BigDecimal netIn = input.subtract(dnTax);
        return new TaxSummary(from, to, output, cnTax, netOut, input, dnTax, netIn, netOut.subtract(netIn),
                sales.size(), purchases.size());
    }

    // ---------- balances ----------

    /** Every customer / vendor of the company; showZero = false leaves out parties with nothing open. */
    @Transactional(readOnly = true)
    public List<BalanceRow> balances(EParty party, Long requestedCompanyId, boolean showZero) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        Map<Long, BigDecimal> open = new HashMap<>();
        for (String s : OPEN) {
            for (Invoice i : commerce.invoices(side(party), company, null, s, null, null)) {
                open.merge(i.partyId(), i.balance(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> available = new HashMap<>();
        for (Object[] r : notes.availableByParty(company, noteKind(party))) {
            available.put((Long) r[0], (BigDecimal) r[1]);
        }
        List<BalanceRow> rows = new ArrayList<>();
        if (party == EParty.CUSTOMER) {
            for (Customer c : customers.search(company, "", Pageable.unpaged())) {
                rows.add(balanceRow(c.getId(), c.getCustomerCode(), c.getCompanyName(), open, available));
            }
        } else {
            for (Vendor v : vendors.search(company, "", Pageable.unpaged())) {
                rows.add(balanceRow(v.getId(), v.getVendorCode(), v.getCompanyName(), open, available));
            }
        }
        return rows.stream()
                .filter(r -> showZero || r.openInvoices().signum() != 0 || r.unappliedNotes().signum() != 0)
                .sorted(Comparator.comparing(r -> r.name() == null ? "" : r.name()))
                .toList();
    }

    // ---------- customer / vendor report ----------

    /**
     * For [from, to]: invoiced (issued invoices), returns (completed), notes (approved), payments (cleared, cash
     * amount); balance = today's open invoice balances - unapplied notes (all dates).
     */
    @Transactional(readOnly = true)
    public PartyReport partyReport(EParty party, Long partyId, LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        String[] p = requireParty(party, partyId, null);
        Long company = Long.valueOf(p[2]);
        ESide side = side(party);
        List<DocLine> inv = issued(side, company, partyId, from, to).stream()
                .map(i -> new DocLine(i.id().toString(), i.number(), i.date(), i.total(), i.status())).toList();
        List<DocLine> ret = commerce.returns(side, company, partyId, "COMPLETED", from, to).stream()
                .map(r -> new DocLine(r.id().toString(), r.number(), r.date(), r.total(), r.status())).toList();
        List<AccountNote> ns = notes.approvedBetween(company, noteKind(party), partyId, from, to);
        List<DocLine> noteDocs = ns.stream().map(n -> new DocLine(n.getId().toString(), n.getNumber(),
                n.getNoteDate(), n.getTotal(), n.getStatus().name())).toList();
        List<Payment> ps = payments.clearedBetween(company,
                party == EParty.CUSTOMER ? EPaymentKind.CUSTOMER : EPaymentKind.VENDOR, partyId, from, to);
        List<DocLine> payDocs = ps.stream().map(x -> new DocLine(x.getId().toString(), x.getNumber(),
                x.getPaymentDate(), x.getAmount(), x.getStatus().name())).toList();
        BigDecimal open = BigDecimal.ZERO;
        for (String s : OPEN) {
            open = open.add(sum(commerce.invoices(side, company, partyId, s, null, null).stream()
                    .map(Invoice::balance).toList()));
        }
        BigDecimal unapplied = BigDecimal.ZERO;
        for (Object[] r : notes.availableByParty(company, noteKind(party))) {
            if (partyId.equals(r[0])) {
                unapplied = (BigDecimal) r[1];
            }
        }
        return new PartyReport(party, partyId.toString(), p[0], p[1], from, to, amounts(inv), amounts(ret),
                amounts(noteDocs), amounts(payDocs), open.subtract(unapplied), inv, ret, noteDocs, payDocs);
    }

    // ---------- helpers ----------

    /** [code, name, companyId]; 404 when not of the caller's (or the given) company. */
    private String[] requireParty(EParty party, Long partyId, Long company) {
        if (party == EParty.CUSTOMER) {
            Customer c = customerService.require(partyId);
            if (company != null && !company.equals(c.getCompanyId())) {
                throw ApiException.notFound(EAccountingError.ACC_002);
            }
            return new String[]{c.getCustomerCode(), c.getCompanyName(), c.getCompanyId().toString()};
        }
        Vendor v = vendorService.require(partyId);
        if (company != null && !company.equals(v.getCompanyId())) {
            throw ApiException.notFound(EAccountingError.ACC_004);
        }
        return new String[]{v.getVendorCode(), v.getCompanyName(), v.getCompanyId().toString()};
    }

    private List<Invoice> issued(ESide side, Long company, Long partyId, LocalDate from, LocalDate to) {
        List<Invoice> out = new ArrayList<>();
        for (String s : ISSUED) {
            out.addAll(commerce.invoices(side, company, partyId, s, from, to));
        }
        return out;
    }

    private BigDecimal noteTax(Long company, ENoteKind kind, LocalDate from, LocalDate to) {
        BigDecimal t = BigDecimal.ZERO;
        for (AccountNote n : notes.search(company, kind, null, null, from, to, Pageable.unpaged())) {
            if (n.getStatus() != com.terp.erp.accounting.note.ENoteStatus.DRAFT) {
                t = t.add(n.getTaxAmount());
            }
        }
        return t;
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EAccountingError.ACC_065, MAX_DAYS);
        }
    }

    private static BalanceRow balanceRow(Long id, String code, String name, Map<Long, BigDecimal> open,
                                         Map<Long, BigDecimal> available) {
        BigDecimal o = open.getOrDefault(id, BigDecimal.ZERO);
        BigDecimal a = available.getOrDefault(id, BigDecimal.ZERO);
        return new BalanceRow(id.toString(), code, name, o, a, o.subtract(a));
    }

    private static BigDecimal amounts(List<DocLine> docs) {
        return sum(docs.stream().map(DocLine::amount).toList());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] a = new BigDecimal[6];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static AgingRow row(String id, String name, BigDecimal[] v) {
        return new AgingRow(id, name, v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    static ESide side(EParty party) {
        return party == EParty.CUSTOMER ? ESide.SALES : ESide.PURCHASE;
    }

    private static ENoteKind noteKind(EParty party) {
        return party == EParty.CUSTOMER ? ENoteKind.CREDIT : ENoteKind.DEBIT;
    }
}
