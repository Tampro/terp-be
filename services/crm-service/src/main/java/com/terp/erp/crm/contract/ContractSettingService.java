package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.security.TenantGuard;

/**
 * Contract numbering per company: prefix + 4 digits. The counter is taken under a row lock and only ever grows, so a
 * number is never reused, also after a prefix change or a deleted contract.
 */
@Service
@RequiredArgsConstructor
public class ContractSettingService {
    private final ContractSettingRepository settings;
    private final ContractRepository contracts;

    public record SettingForm(
            @Schema(description = "Super admin only: the company to act on", example = "10") Long companyId,
            @Schema(description = "Number prefix, 1 to 10 capital letters; applies to the next numbers", example = "CON",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "[A-Z]{1,10}") String prefix) {
    }

    public record SettingDto(String companyId, String prefix, int nextNumber, String nextContractNumber) {
        static SettingDto of(ContractSetting s) {
            return new SettingDto(s.getCompanyId().toString(), s.getPrefix(), s.getNextNumber(),
                    s.format(s.getNextNumber()));
        }
    }

    @Transactional(readOnly = true)
    public SettingDto get(Long companyId) {
        Long company = TenantGuard.resolveCompany(companyId, EContractError.CTR_001);
        return SettingDto.of(settings.findById(company).orElseGet(() -> fresh(company)));
    }

    @Transactional
    public SettingDto update(SettingForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), EContractError.CTR_001);
        ContractSetting s = locked(company);
        s.setPrefix(f.prefix());
        return SettingDto.of(s);
    }

    /** The next free number of the company, inside the caller's transaction (the lock lasts until it commits). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String take(Long companyId) {
        ContractSetting s = locked(companyId);
        while (true) {
            String number = s.format(s.getNextNumber());
            s.setNextNumber(s.getNextNumber() + 1);
            // Switching back to an older prefix could meet a number already given; skip it rather than fail.
            if (!contracts.existsByCompanyIdAndNumber(companyId, number)) {
                return number;
            }
        }
    }

    private ContractSetting locked(Long company) {
        return settings.lock(company).orElseGet(() -> settings.saveAndFlush(fresh(company)));
    }

    private static ContractSetting fresh(Long company) {
        ContractSetting s = new ContractSetting();
        s.setCompanyId(company);
        return s;
    }
}
