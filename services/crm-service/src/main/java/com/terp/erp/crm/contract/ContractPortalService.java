package com.terp.erp.crm.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Client portal: contracts whose customer is the accounting customer linked to the caller. The customer comes from
 * accounting-service (never from the request); another customer's contract answers 404 (CTR_002).
 * Hidden from the client: comments, notes, attachments, renewal notes, created_by and every internal id but the
 * contract's own.
 */
@Service
@RequiredArgsConstructor
public class ContractPortalService {
    private final ContractRepository contracts;
    private final ContractRenewalRepository renewals;
    private final ContractTypeService types;
    private final AccountingClient accounting;

    public record PortalRenewal(LocalDate startDate, LocalDate endDate, BigDecimal value, Contract.EStatus status) {
    }

    public record PortalContract(String id, String number, String subject, String contractTypeName, BigDecimal value,
                                 LocalDate startDate, LocalDate endDate, Contract.EStatus status) {
    }

    public record PortalContractDetail(PortalContract contract, String description, List<PortalRenewal> renewals) {
    }

    @Transactional(readOnly = true)
    public Page<PortalContract> list(Pageable pageable) {
        Long customer = accounting.myCustomerId();
        return contracts.findByCompanyIdAndCustomerIdAndDeletedFalse(CurrentUser.get().companyId(), customer, pageable)
                .map(this::row);
    }

    @Transactional(readOnly = true)
    public PortalContractDetail get(Long id) {
        Contract c = requireMine(id);
        return new PortalContractDetail(row(c), c.getDescription(),
                renewals.findByContractIdAndDeletedFalseOrderByIdAsc(c.getId()).stream()
                        .map(r -> new PortalRenewal(r.getStartDate(), r.getEndDate(), r.getValue(), r.getStatus()))
                        .toList());
    }

    /** A contract of the customer linked to me, else 404 (CTR_002); used by signing and the PDF too. */
    Contract requireMine(Long id) {
        Long customer = accounting.myCustomerId();
        return contracts.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), CurrentUser.get().companyId())
                        && Objects.equals(x.getCustomerId(), customer))
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_002));
    }

    private PortalContract row(Contract c) {
        return new PortalContract(String.valueOf(c.getId()), c.getNumber(), c.getSubject(),
                types.nameOf(c.getContractTypeId()), c.getValue(), c.getStartDate(), c.getEndDate(), c.getStatus());
    }
}
