package com.terp.erp.iam.ip;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.CompanyAllowedIp;
import com.terp.erp.iam.model.CompanyIpRestriction;
import com.terp.erp.iam.repository.CompanyAllowedIpRepository;
import com.terp.erp.iam.repository.CompanyIpRestrictionRepository;
import com.terp.erp.iam.settings.SettingsService;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * IP Restriction Settings (docs/analysis/06 §10.5.6; PO 2026-11-21). Per company: when the platform switch (System
 * Settings) and the company switch are on and the list is not empty, the company's users sign in and refresh tokens
 * only from a listed address or range (AUTH_012); super admins are not restricted. A change that would leave the
 * caller's own address outside an enforced list is refused (IAM_021), so a company cannot lock itself out.
 */
@Service
@RequiredArgsConstructor
public class IpRestrictionService {
    private final CompanyIpRestrictionRepository switches;
    private final CompanyAllowedIpRepository ips;
    private final SettingsService settings;
    private final Clock clock;

    public record IpForm(@Schema(description = "IPv4 / IPv6 address or CIDR range", example = "203.0.113.0/24",
            requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String ipAddress) {
    }

    public record SwitchForm(boolean enabled) {
    }

    public record IpDto(String id, String ipAddress, Instant createdDate) {
    }

    /** enforced = what login applies now; yourIp = the caller's address as IAM sees it. */
    public record Status(boolean enabled, boolean platformEnabled, int addresses, boolean enforced, String yourIp) {
    }

    // ------------------------------------------------------------------ enforcement

    /** Called at login / refresh for a company user; AUTH_012 when the address is not allowed. */
    @Transactional(readOnly = true)
    public void assertLoginAllowed(Long companyId, String clientIp) {
        List<IpRange> ranges = enforcedRanges(companyId);
        if (ranges.isEmpty()) {
            return;
        }
        byte[] addr = IpRange.literal(clientIp);
        if (ranges.stream().noneMatch(r -> r.contains(addr))) {
            throw ApiException.forbidden(EIamError.AUTH_012);
        }
    }

    // ------------------------------------------------------------------ settings page

    @Transactional(readOnly = true)
    public Status status(Long requestedCompanyId, String yourIp) {
        Long companyId = company(requestedCompanyId);
        boolean on = switches.findById(Nulls.nonNull(companyId)).map(x -> x.isEnabled()).orElse(false);
        int n = ips.findByCompanyId(companyId).size();
        return new Status(on, settings.system().ipRestrictionOn(), n, !enforcedRanges(companyId).isEmpty(), yourIp);
    }

    @Transactional(readOnly = true)
    public Page<IpDto> list(Long requestedCompanyId, Pageable pageable) {
        return ips.findByCompanyId(company(requestedCompanyId), pageable).map(IpRestrictionService::dto);
    }

    @Transactional
    public Status setEnabled(Long requestedCompanyId, boolean enabled, String yourIp) {
        Long companyId = company(requestedCompanyId);
        CompanyIpRestriction s = switches.findById(Nulls.nonNull(companyId)).orElseGet(() -> {
            CompanyIpRestriction n = new CompanyIpRestriction();
            n.setCompanyId(companyId);
            return n;
        });
        if (enabled) {
            guard(companyId, rangesOf(ips.findByCompanyId(companyId)), yourIp);
        }
        s.setEnabled(enabled);
        s.setUpdatedBy(CurrentUser.find().map(p -> p.keycloakId()).orElse(null));
        s.setUpdatedDate(clock.instant());
        switches.save(s);
        return status(companyId, yourIp);
    }

    @Transactional
    public IpDto add(Long requestedCompanyId, IpForm form) {
        Long companyId = company(requestedCompanyId);
        String text = IpRange.parse(form.ipAddress()).text();
        if (ips.existsByCompanyIdAndIpAddressAndIdNot(companyId, text, -1L)) {
            throw ApiException.badRequest(EIamError.IAM_022);
        }
        CompanyAllowedIp a = new CompanyAllowedIp();
        a.setCompanyId(companyId);
        a.setIpAddress(text);
        a.setCreatedBy(CurrentUser.find().map(p -> p.keycloakId()).orElse(null));
        a.setCreatedDate(clock.instant());
        return dto(ips.save(a));
    }

    /** Edit: the new value must still let the caller in when the list is enforced. */
    @Transactional
    public IpDto update(Long id, IpForm form, String yourIp) {
        CompanyAllowedIp a = require(id);
        String text = IpRange.parse(form.ipAddress()).text();
        if (ips.existsByCompanyIdAndIpAddressAndIdNot(a.getCompanyId(), text, a.getId())) {
            throw ApiException.badRequest(EIamError.IAM_022);
        }
        List<CompanyAllowedIp> after = new ArrayList<>(ips.findByCompanyId(a.getCompanyId()));
        after.removeIf(x -> x.getId().equals(a.getId()));
        List<IpRange> ranges = rangesOf(after);
        ranges.add(IpRange.parse(text));
        if (companyEnforces(a.getCompanyId())) {
            guard(a.getCompanyId(), ranges, yourIp);
        }
        a.setIpAddress(text);
        return dto(a);
    }

    @Transactional
    public void delete(Long id, String yourIp) {
        CompanyAllowedIp a = require(id);
        List<CompanyAllowedIp> after = new ArrayList<>(ips.findByCompanyId(a.getCompanyId()));
        after.removeIf(x -> x.getId().equals(a.getId()));
        if (companyEnforces(a.getCompanyId()) && !after.isEmpty()) {
            guard(a.getCompanyId(), rangesOf(after), yourIp);
        }
        ips.delete(a);
    }

    // ------------------------------------------------------------------ helpers

    private List<IpRange> enforcedRanges(Long companyId) {
        if (!companyEnforces(companyId)) {
            return List.of();
        }
        return rangesOf(ips.findByCompanyId(companyId));
    }

    private boolean companyEnforces(Long companyId) {
        return settings.system().ipRestrictionOn()
                && switches.findById(Nulls.nonNull(companyId)).map(x -> x.isEnabled()).orElse(false);
    }

    /** A company user must stay able to sign in; a super admin is never restricted. */
    private static void guard(Long companyId, List<IpRange> ranges, String yourIp) {
        ErpPrincipal me = CurrentUser.find().orElse(null);
        if (me == null || me.superAdmin() || ranges.isEmpty()) {
            return;
        }
        byte[] addr = IpRange.literal(yourIp);
        if (ranges.stream().noneMatch(r -> r.contains(addr))) {
            throw ApiException.badRequest(EIamError.IAM_021, yourIp == null ? "?" : yourIp);
        }
    }

    private static List<IpRange> rangesOf(List<CompanyAllowedIp> list) {
        List<IpRange> out = new ArrayList<>();
        for (CompanyAllowedIp a : list) {
            out.add(IpRange.parse(a.getIpAddress()));
        }
        return out;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EIamError.IAM_003);
    }

    private CompanyAllowedIp require(Long id) {
        CompanyAllowedIp a = ips.findById(Nulls.nonNull(id)).orElseThrow(() -> ApiException.notFound(EIamError.IAM_023));
        TenantGuard.assertSameCompany(a.getCompanyId(), EIamError.IAM_023);
        return a;
    }

    private static IpDto dto(CompanyAllowedIp a) {
        return new IpDto(a.getId().toString(), a.getIpAddress(), a.getCreatedDate());
    }
}
