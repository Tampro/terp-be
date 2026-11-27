package com.terp.erp.store.shop;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** One public shop per company; its slug is unique across every company. */
@Service
@RequiredArgsConstructor
public class ShopSettingService {
    static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");
    // TODO(assumption): reserved addresses (Proposal 2026-11-28); code, so extending it needs no migration.
    static final Set<String> RESERVED = Set.of("admin", "api", "www", "shop", "store", "public", "static", "app");

    private final ShopSettingRepository settings;
    private final PlatformTransactionManager txManager;

    public record ShopSettingForm(
            @Schema(description = "a-z, 0-9 and '-', 3-40, not starting or ending with '-'; stored lower-cased; unique "
                    + "across companies", example = "homeplus-express", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 40) String slug,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled) {
    }

    /** slug null and enabled false while the company never saved its shop. */
    public record ShopSettingDto(String companyId, String slug, boolean enabled, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public ShopSettingDto get(Long requestedCompanyId) {
        Long companyId = Texts.company(requestedCompanyId);
        return settings.findByCompanyId(companyId).map(ShopSettingService::dto)
                .orElse(new ShopSettingDto(Texts.id(companyId), null, false, null));
    }

    /** Full replacement of slug and enabled; creates the row on the first save. */
    public ShopSettingDto put(Long requestedCompanyId, ShopSettingForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        String slug = f.slug().trim().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
        if (RESERVED.contains(slug)) {
            throw ApiException.badRequest(EStoreError.STO_015);
        }
        try {
            return save(companyId, slug, f.enabled());
        } catch (DataIntegrityViolationException e) {
            if (msg(e).contains("uk_shop_setting_slug")) {
                throw ApiException.badRequest(EStoreError.STO_014);
            }
            // The same company's first save raced itself (double click): the row exists now, apply onto it once.
            try {
                return save(companyId, slug, f.enabled());
            } catch (DataIntegrityViolationException again) {
                throw ApiException.badRequest(EStoreError.STO_014);
            }
        }
    }

    private ShopSettingDto save(Long companyId, String slug, boolean enabled) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
            if (settings.existsBySlugAndCompanyIdNot(slug, companyId)) {
                throw ApiException.badRequest(EStoreError.STO_014);
            }
            ShopSetting s = settings.findByCompanyId(companyId).orElseGet(() -> {
                ShopSetting n = new ShopSetting();
                n.setCompanyId(companyId);
                return n;
            });
            s.setSlug(slug);
            s.setEnabled(enabled);
            return dto(settings.saveAndFlush(s));
        });
    }

    private static String msg(DataIntegrityViolationException e) {
        return String.valueOf(e.getMostSpecificCause().getMessage());
    }

    private static ShopSettingDto dto(ShopSetting s) {
        return new ShopSettingDto(Texts.id(s.getCompanyId()), s.getSlug(), s.isEnabled(), s.getUpdatedDate());
    }
}
