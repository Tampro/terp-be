package com.terp.erp.crm.record;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client portal: deals with a CLIENT link to the accounting customer linked to the caller (lead to deal conversion
 * and the deal form write that link). Read-only. Hidden: notes, phone, members, labels, sources, products, tasks,
 * calls, discussions, logged emails, files and the activity log (all internal to the sales team).
 */
@Service
@RequiredArgsConstructor
public class DealPortalService {
    private final DealRepository deals;
    private final CrmLinkRepository links;
    private final SetupService setup;
    private final AccountingClient accounting;

    public record PortalDeal(String id, String name, BigDecimal price, EDealStatus status, String stageName,
                             Instant createdDate) {
    }

    @Transactional(readOnly = true)
    public Page<PortalDeal> list(Pageable pageable) {
        Long customer = accounting.myCustomerId();
        Page<Deal> page = deals.findOfClient(CurrentUser.get().companyId(), customer, ESubject.DEAL, ELinkType.CLIENT,
                pageable);
        Map<Long, String> names = setup.stageNames(page.getContent().stream().map(Deal::getStageId).distinct().toList());
        return page.map(d -> dto(d, names));
    }

    @Transactional(readOnly = true)
    public PortalDeal get(Long id) {
        Long customer = accounting.myCustomerId();
        Deal d = deals.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), CurrentUser.get().companyId())
                        && links.existsBySubjectTypeAndSubjectIdAndLinkTypeAndRefId(ESubject.DEAL, x.getId(),
                        ELinkType.CLIENT, customer))
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_007));
        return dto(d, setup.stageNames(List.of(d.getStageId())));
    }

    private static PortalDeal dto(Deal d, Map<Long, String> stageNames) {
        return new PortalDeal(d.getId().toString(), d.getName(), d.getPrice(), d.getStatus(),
                stageNames.get(d.getStageId()), d.getCreatedDate());
    }
}
