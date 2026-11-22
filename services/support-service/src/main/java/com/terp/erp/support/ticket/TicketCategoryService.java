package com.terp.erp.support.ticket;

import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Ticket categories (reference TicketCategoryController: name, color). */
@Service
@RequiredArgsConstructor
public class TicketCategoryService {
    public record CategoryDto(String id, String name, String color) {
        static CategoryDto of(TicketCategory c) {
            return new CategoryDto(c.getId().toString(), c.getName(), c.getColor());
        }
    }

    public record CategoryForm(
            @Schema(example = "Billing", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(description = "#RRGGBB", example = "#10B77F", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
    }

    private final TicketCategoryRepository categories;
    private final TicketRepository tickets;

    @Transactional(readOnly = true)
    public List<CategoryDto> list(Long requestedCompanyId) {
        return listOf(Texts.company(requestedCompanyId));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listOf(Long companyId) {
        return categories.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream().map(CategoryDto::of).toList();
    }

    @Transactional
    public CategoryDto create(Long requestedCompanyId, CategoryForm f) {
        TicketCategory c = new TicketCategory();
        c.setCompanyId(Texts.company(requestedCompanyId));
        apply(c, f);
        return CategoryDto.of(categories.save(c));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryForm f) {
        TicketCategory c = require(id);
        apply(c, f);
        return CategoryDto.of(c);
    }

    /** TODO(assumption): a used category cannot be deleted (the reference sets the tickets' category to null). */
    @Transactional
    public void delete(Long id) {
        TicketCategory c = require(id);
        if (tickets.existsByCategoryIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ESupportError.SUP_013);
        }
        c.setDeleted(true);
    }

    /** A category the ticket may use: of the same company, not deleted; otherwise SUP_005 (400, a form value). */
    public TicketCategory requireForTicket(Long categoryId, Long companyId) {
        return categories.findByIdAndDeletedFalse(categoryId)
                .filter(c -> Objects.equals(c.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(ESupportError.SUP_005));
    }

    private TicketCategory require(Long id) {
        TicketCategory c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_005));
        TenantGuard.assertSameCompany(c.getCompanyId(), ESupportError.SUP_005);
        return c;
    }

    private static void apply(TicketCategory c, CategoryForm f) {
        c.setName(f.name().trim());
        c.setColor(f.color().toUpperCase(java.util.Locale.ROOT));
    }
}
