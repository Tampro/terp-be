package com.terp.erp.document.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.document.TestLogin;
import com.terp.erp.document.category.DocumentCategory;
import com.terp.erp.document.category.DocumentCategoryRepository;
import com.terp.erp.document.category.DocumentCategoryService;
import com.terp.erp.document.category.DocumentCategoryService.CategoryForm;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.contract.ContractTypeService.TypeForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryTypeServiceTest {
    private final DocumentCategoryRepository categories = mock(DocumentCategoryRepository.class);
    private final ContractTypeRepository types = mock(ContractTypeRepository.class);
    private final DocumentCategoryService categoryService = new DocumentCategoryService(categories,
            mock(com.terp.erp.document.hr.HrDocumentRepository.class),
            mock(com.terp.erp.document.template.DocTemplateRepository.class));
    private final ContractTypeService typeService = new ContractTypeService(types,
            mock(EmployeeContractRepository.class),
            mock(com.terp.erp.document.template.DocTemplateRepository.class), java.time.Clock.systemUTC());

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "DOCUMENT_MANAGE", "CONTRACT_MANAGE");
        when(categories.save(any())).thenAnswer(i -> {
            DocumentCategory c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(types.save(any())).thenAnswer(i -> {
            ContractType t = i.getArgument(0);
            t.setId(2L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void categoryDefaultsAndRules() {
        var c = categoryService.create(null, new CategoryForm(" HR Policies ", null, "#22c55e", null, true, null));
        assertThat(c.name()).isEqualTo("HR Policies");
        assertThat(c.color()).isEqualTo("#22C55E");
        assertThat(c.icon()).isEqualTo("folder");
        assertThat(c.mandatory()).isTrue();
        assertThat(c.status()).isEqualTo(EStatus.ACTIVE);
        when(categories.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(), anyLong()))
                .thenReturn(true);
        assertCode(() -> categoryService.create(null, new CategoryForm("HR Policies", null, null, null, null, null)),
                EDocumentError.DOC_003);
        DocumentCategory other = new DocumentCategory();
        other.setId(5L);
        other.setCompanyId(11L);
        when(categories.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(other));
        assertCode(() -> categoryService.get(5L), EDocumentError.DOC_002);
    }

    @Test
    void contractTypeDefaultsKeepAndCounts() {
        var t = typeService.create(null, new TypeForm("Permanent", null, null, null, null, null, null));
        assertThat(t.defaultDurationMonths()).isNull();
        assertThat(t.probationMonths()).isZero();
        assertThat(t.renewable()).isFalse();
        ContractType stored = new ContractType();
        stored.setId(2L);
        stored.setCompanyId(10L);
        stored.setRenewable(true);
        stored.setStatus(EStatus.INACTIVE);
        when(types.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(stored));
        var edited = typeService.update(2L, new TypeForm("Fixed 12", null, 12, 2, 30, null, null));
        assertThat(edited.renewable()).isTrue();
        assertThat(edited.status()).isEqualTo(EStatus.INACTIVE);
        assertThat(edited.defaultDurationMonths()).isEqualTo(12);
        when(types.countByStatus(eq(10L), any(), anyString())).thenReturn(List.<Object[]>of(new Object[]{EStatus.ACTIVE, 3L}));
        var counts = typeService.statusCounts(null, null, null);
        assertThat(counts.all()).isEqualTo(3);
        assertThat(counts.byStatus().get(EStatus.INACTIVE)).isZero();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EDocumentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
