package com.terp.erp.commerce.portal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The customer / vendor of the caller, asked of accounting-service with the caller's token and cached per
 * (company, user) for 60 s. A failed lookup (not linked, accounting down) is never cached: Caffeine stores nothing
 * when the loader throws. A re-link in accounting is seen here within 60 s.
 */
@Component
public class PortalPartyResolver {
    private final AccountingClient accounting;
    private final Cache<String, AccountingClient.Party> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(10_000).build();

    public PortalPartyResolver(AccountingClient accounting) {
        this.accounting = accounting;
    }

    /** Company id and customer id of the caller's linked customer; 404 SAL_026 when only a vendor is linked. */
    public Mine customer() {
        AccountingClient.Party p = party();
        if (p.customerId() == null) {
            throw ApiException.notFound(ECommerceError.SAL_026);
        }
        return new Mine(company(), Long.valueOf(p.customerId()), p.customerName());
    }

    /** Company id and vendor id of the caller's linked vendor; 404 PUR_018 when only a customer is linked. */
    public Mine vendor() {
        AccountingClient.Party p = party();
        if (p.vendorId() == null) {
            throw ApiException.notFound(EPurchaseError.PUR_018);
        }
        return new Mine(company(), Long.valueOf(p.vendorId()), p.vendorName());
    }

    public record Mine(Long companyId, Long partyId, String name) {
    }

    private AccountingClient.Party party() {
        ErpPrincipal me = CurrentUser.get();
        if (me.companyId() == null || me.userId() == null) {
            // super admin or an account without a local user: nothing to key on, accounting answers for itself
            return accounting.myParty();
        }
        return cache.get(me.companyId() + ":" + me.userId(), k -> accounting.myParty());
    }

    private static Long company() {
        return CurrentUser.get().companyId();
    }
}
