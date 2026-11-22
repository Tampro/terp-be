package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.LedgerSetup;
import com.terp.erp.accounting.ledger.LedgerSetupRepository;
import com.terp.erp.accounting.ledger.SystemAccountMapping;
import com.terp.erp.accounting.ledger.SystemAccountMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.terp.erp.accounting.coa.EAccountCategory.ASSETS;
import static com.terp.erp.accounting.coa.EAccountCategory.EQUITY;
import static com.terp.erp.accounting.coa.EAccountCategory.EXPENSES;
import static com.terp.erp.accounting.coa.EAccountCategory.LIABILITIES;
import static com.terp.erp.accounting.coa.EAccountCategory.REVENUE;
import static com.terp.erp.accounting.coa.ENormalBalance.CREDIT;
import static com.terp.erp.accounting.coa.ENormalBalance.DEBIT;

/**
 * Seeds a company's default chart of accounts and system account mapping the first time the ledger is used
 * (no hook in IAM). The ledger_setup row is locked meanwhile, so it runs once per company.
 */
// TODO(assumption): VAS TT200 subset — confirm with the chief accountant (Q-07).
@Service
@RequiredArgsConstructor
public class VasChartSeeder {
    private record TypeSeed(String code, EAccountCategory category, String name, ENormalBalance normal) {
    }

    private record AccountSeed(String code, String parent, String type, String name, String english,
                               ENormalBalance normal) {
    }

    private static final List<TypeSeed> TYPES = List.of(
            new TypeSeed("CA", ASSETS, "Tài sản ngắn hạn", DEBIT),
            new TypeSeed("NCA", ASSETS, "Tài sản dài hạn", DEBIT),
            new TypeSeed("LIA", LIABILITIES, "Nợ phải trả", CREDIT),
            new TypeSeed("EQ", EQUITY, "Vốn chủ sở hữu", CREDIT),
            new TypeSeed("REV", REVENUE, "Doanh thu", CREDIT),
            new TypeSeed("OI", REVENUE, "Thu nhập khác", CREDIT),
            new TypeSeed("COST", EXPENSES, "Chi phí sản xuất, kinh doanh", DEBIT),
            new TypeSeed("OE", EXPENSES, "Chi phí khác", DEBIT));

    /** Parents come before their children. */
    private static final List<AccountSeed> ACCOUNTS = List.of(
            a("111", null, "CA", "Tiền mặt", "Cash on hand", DEBIT),
            a("112", null, "CA", "Tiền gửi ngân hàng", "Cash in bank", DEBIT),
            a("113", null, "CA", "Tiền đang chuyển", "Cash in transit", DEBIT),
            a("121", null, "CA", "Chứng khoán kinh doanh", "Trading securities", DEBIT),
            a("128", null, "CA", "Đầu tư nắm giữ đến ngày đáo hạn", "Held-to-maturity investments", DEBIT),
            a("131", null, "CA", "Phải thu của khách hàng", "Accounts receivable", DEBIT),
            a("133", null, "CA", "Thuế GTGT được khấu trừ", "Deductible VAT", DEBIT),
            a("1331", "133", "CA", "Thuế GTGT được khấu trừ của hàng hóa, dịch vụ", "Input VAT on goods and services",
                    DEBIT),
            a("1332", "133", "CA", "Thuế GTGT được khấu trừ của TSCĐ", "Input VAT on fixed assets", DEBIT),
            a("136", null, "CA", "Phải thu nội bộ", "Intercompany receivables", DEBIT),
            a("138", null, "CA", "Phải thu khác", "Other receivables", DEBIT),
            a("141", null, "CA", "Tạm ứng", "Advances to employees", DEBIT),
            a("151", null, "CA", "Hàng mua đang đi đường", "Goods in transit", DEBIT),
            a("152", null, "CA", "Nguyên liệu, vật liệu", "Raw materials", DEBIT),
            a("153", null, "CA", "Công cụ, dụng cụ", "Tools and supplies", DEBIT),
            a("154", null, "CA", "Chi phí sản xuất, kinh doanh dở dang", "Work in progress", DEBIT),
            a("155", null, "CA", "Thành phẩm", "Finished goods", DEBIT),
            a("156", null, "CA", "Hàng hóa", "Merchandise inventory", DEBIT),
            a("157", null, "CA", "Hàng gửi đi bán", "Goods on consignment", DEBIT),
            a("211", null, "NCA", "Tài sản cố định hữu hình", "Tangible fixed assets", DEBIT),
            a("213", null, "NCA", "Tài sản cố định vô hình", "Intangible fixed assets", DEBIT),
            a("214", null, "NCA", "Hao mòn tài sản cố định", "Accumulated depreciation (contra asset)", CREDIT),
            a("242", null, "NCA", "Chi phí trả trước", "Prepaid expenses", DEBIT),
            a("331", null, "LIA", "Phải trả cho người bán", "Accounts payable", CREDIT),
            a("333", null, "LIA", "Thuế và các khoản phải nộp Nhà nước", "Taxes payable to the State", CREDIT),
            a("3331", "333", "LIA", "Thuế giá trị gia tăng phải nộp", "VAT payable", CREDIT),
            a("33311", "3331", "LIA", "Thuế GTGT đầu ra", "Output VAT", CREDIT),
            a("3334", "333", "LIA", "Thuế thu nhập doanh nghiệp", "Corporate income tax payable", CREDIT),
            a("3335", "333", "LIA", "Thuế thu nhập cá nhân", "Personal income tax payable", CREDIT),
            a("334", null, "LIA", "Phải trả người lao động", "Payables to employees", CREDIT),
            a("335", null, "LIA", "Chi phí phải trả", "Accrued expenses", CREDIT),
            a("338", null, "LIA", "Phải trả, phải nộp khác", "Other payables", CREDIT),
            a("3383", "338", "LIA", "Bảo hiểm xã hội", "Social insurance payable", CREDIT),
            a("341", null, "LIA", "Vay và nợ thuê tài chính", "Borrowings and finance lease liabilities", CREDIT),
            a("411", null, "EQ", "Vốn đầu tư của chủ sở hữu", "Owner's invested capital", CREDIT),
            a("4111", "411", "EQ", "Vốn góp của chủ sở hữu", "Contributed capital", CREDIT),
            a("421", null, "EQ", "Lợi nhuận sau thuế chưa phân phối", "Retained earnings", CREDIT),
            a("911", null, "EQ", "Xác định kết quả kinh doanh", "Income summary", CREDIT),
            a("511", null, "REV", "Doanh thu bán hàng và cung cấp dịch vụ", "Sales and service revenue", CREDIT),
            a("5111", "511", "REV", "Doanh thu bán hàng hóa", "Sales of goods", CREDIT),
            a("5113", "511", "REV", "Doanh thu cung cấp dịch vụ", "Service revenue", CREDIT),
            a("515", null, "REV", "Doanh thu hoạt động tài chính", "Financial income", CREDIT),
            a("521", null, "REV", "Các khoản giảm trừ doanh thu", "Revenue deductions (contra revenue)", DEBIT),
            a("711", null, "OI", "Thu nhập khác", "Other income", CREDIT),
            a("632", null, "COST", "Giá vốn hàng bán", "Cost of goods sold", DEBIT),
            a("635", null, "COST", "Chi phí tài chính", "Financial expenses", DEBIT),
            a("641", null, "COST", "Chi phí bán hàng", "Selling expenses", DEBIT),
            a("642", null, "COST", "Chi phí quản lý doanh nghiệp", "General and administrative expenses", DEBIT),
            a("6427", "642", "COST", "Chi phí dịch vụ mua ngoài", "Outside services (incl. bank charges)", DEBIT),
            a("6428", "642", "COST", "Chi phí bằng tiền khác", "Other cash expenses", DEBIT),
            a("811", null, "OE", "Chi phí khác", "Other expenses", DEBIT),
            a("821", null, "OE", "Chi phí thuế thu nhập doanh nghiệp", "Corporate income tax expense", DEBIT));

    private final LedgerSetupRepository setups;
    private final AccountTypeRepository types;
    private final ChartAccountRepository accounts;
    private final SystemAccountMappingRepository mappings;

    /** Seeds the company's chart and mapping unless already done; safe to call on every posting. */
    @Transactional
    public void ensureSeeded(Long companyId) {
        setups.ensureRow(companyId);
        LedgerSetup setup = setups.lockByCompanyId(companyId).orElseThrow();
        if (setup.isSeeded()) {
            return;
        }
        Map<String, Long> typeIds = new HashMap<>();
        for (TypeSeed t : TYPES) {
            AccountType row = new AccountType();
            row.setCompanyId(companyId);
            row.setCode(t.code());
            row.setCategory(t.category());
            row.setName(t.name());
            row.setNormalBalance(t.normal());
            typeIds.put(t.code(), types.save(row).getId());
        }
        Map<String, Long> accountIds = new HashMap<>();
        for (AccountSeed s : ACCOUNTS) {
            ChartAccount row = new ChartAccount();
            row.setCompanyId(companyId);
            row.setAccountTypeId(typeIds.get(s.type()));
            row.setCode(s.code());
            row.setName(s.name());
            row.setDescription(s.english());
            row.setNormalBalance(s.normal());
            row.setParentId(s.parent() == null ? null : accountIds.get(s.parent()));
            accountIds.put(s.code(), accounts.save(row).getId());
        }
        for (ESystemAccount key : ESystemAccount.values()) {
            SystemAccountMapping m = new SystemAccountMapping();
            m.setCompanyId(companyId);
            m.setSystemKey(key);
            m.setAccountId(accountIds.get(key.defaultCode()));
            mappings.save(m);
        }
        setup.setSeeded(true);
    }

    /** Account codes of the default chart, for tests and documentation. */
    static List<String> defaultCodes() {
        return ACCOUNTS.stream().map(AccountSeed::code).toList();
    }

    private static AccountSeed a(String code, String parent, String type, String name, String english,
                                 ENormalBalance normal) {
        return new AccountSeed(code, parent, type, name, english, normal);
    }
}
