package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Currency;
import com.terp.erp.iam.repository.CurrencyRepository;

import java.time.Instant;
import java.util.List;

/** Currency page (docs/analysis/06 §3.2.5). Exactly one default currency at any time. */
@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencies;

    public record CurrencyDto(String id, String code, String name, String symbol, String description,
                              boolean defaultCurrency, Instant createdDate, Instant updatedDate) {
        static CurrencyDto of(Currency c) {
            return new CurrencyDto(SaasDtos.str(c.getId()), c.getCode(), c.getName(), c.getSymbol(),
                    c.getDescription(), c.isDefaultCurrency(), c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    public record CurrencyForm(
            @Schema(description = "Currency Name", example = "US Dollar", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "ISO 4217 code, 3 upper-case letters", example = "USD",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String code,
            @Schema(description = "Currency Symbol (optional; cleared when omitted on update)", example = "$")
            @Size(max = 10) String symbol,
            @Schema(description = "Description (optional; cleared when omitted on update)", example = "United States dollar")
            @Size(max = 500) String description,
            @Schema(description = "Set as Default Currency: moves the default flag here (the previous default loses it). "
                    + "To change the default, set it on another currency.", example = "false")
            boolean defaultCurrency) {
    }

    @Transactional(readOnly = true)
    public Page<CurrencyDto> search(String keyword, Pageable pageable) {
        return currencies.search(OrderService.kw(keyword), pageable).map(CurrencyDto::of);
    }

    /** For pickers (any signed-in user): default first, then by code. */
    @Transactional(readOnly = true)
    public List<CurrencyDto> all() {
        return currencies.findAllByOrderByDefaultCurrencyDescCodeAsc().stream().map(CurrencyDto::of).toList();
    }

    @Transactional(readOnly = true)
    public CurrencyDto get(Long id) {
        return CurrencyDto.of(require(id));
    }

    @Transactional
    public CurrencyDto create(CurrencyForm form) {
        if (currencies.existsByCode(form.code())) {
            throw ApiException.badRequest(ESaasError.SAAS_023);
        }
        Currency c = new Currency();
        apply(c, form);
        return CurrencyDto.of(currencies.save(c));
    }

    /** Full replacement. The current default cannot be un-defaulted here — another currency must take the flag. */
    @Transactional
    public CurrencyDto update(Long id, CurrencyForm form) {
        Currency c = require(id);
        if (!c.getCode().equals(form.code()) && currencies.existsByCode(form.code())) {
            throw ApiException.badRequest(ESaasError.SAAS_023);
        }
        if (c.isDefaultCurrency() && !form.defaultCurrency()) {
            throw ApiException.badRequest(ESaasError.SAAS_025);
        }
        apply(c, form);
        return CurrencyDto.of(c);
    }

    /** Permanent (as the reference manual says); the default currency cannot be deleted. */
    @Transactional
    public void delete(Long id) {
        Currency c = require(id);
        if (c.isDefaultCurrency()) {
            throw ApiException.badRequest(ESaasError.SAAS_024);
        }
        currencies.delete(c);
    }

    private void apply(Currency c, CurrencyForm f) {
        // The first currency ever created becomes the default, so there is always one.
        boolean makeDefault = f.defaultCurrency() || currencies.findFirstByDefaultCurrencyTrue().isEmpty();
        if (makeDefault) {
            currencies.findFirstByDefaultCurrencyTrue()
                    .filter(d -> !d.getId().equals(c.getId()))
                    .ifPresent(d -> d.setDefaultCurrency(false));
        }
        c.setName(f.name().trim());
        c.setCode(f.code());
        c.setSymbol(f.symbol());
        c.setDescription(f.description());
        c.setDefaultCurrency(makeDefault || c.isDefaultCurrency());
    }

    private Currency require(Long id) {
        return currencies.findById(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_022));
    }
}
