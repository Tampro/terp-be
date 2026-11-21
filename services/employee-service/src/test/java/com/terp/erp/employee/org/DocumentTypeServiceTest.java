package com.terp.erp.employee.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.document.EmployeeDocumentRepository;
import com.terp.erp.employee.org.DocumentTypeDtos.DocumentTypeForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTypeServiceTest {
    private final DocumentTypeRepository types = mock(DocumentTypeRepository.class);
    private final DocumentTypeSettingRepository settings = mock(DocumentTypeSettingRepository.class);
    private final EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
    private final DocumentTypeService service = new DocumentTypeService(types, settings, documents);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void companySettingOverridesTheSystemDefault() {
        TestLogin.as(10L, "ORG_VIEW");
        DocumentType idProof = type(1L, null, false);
        DocumentType permit = type(9L, 10L, true);
        when(types.visibleTo(10L)).thenReturn(List.of(idProof, type(2L, null, false), permit));
        when(settings.findByCompanyId(10L)).thenReturn(List.of(setting(1L, true)));
        assertThat(service.requiredTypeIds(10L)).containsExactlyInAnyOrder(1L, 9L);
        var page = service.search(null, null, true, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(DocumentTypeDtos.DocumentTypeDto::id).containsExactly("1", "9");
    }

    @Test
    void setRequiredOnASystemTypeWritesTheCompanySettingOnly() {
        TestLogin.as(10L, "ORG_MANAGE");
        DocumentType idProof = type(1L, null, false);
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(idProof));
        when(settings.findByCompanyIdAndDocumentTypeId(10L, 1L)).thenReturn(Optional.empty());
        var d = service.setRequired(null, 1L, true);
        assertThat(d.required()).isTrue();
        assertThat(idProof.isRequired()).isFalse();
        verify(settings).save(any());
    }

    @Test
    void systemTypeCannotBeEditedOrDeleted() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(type(1L, null, false)));
        assertError(() -> service.update(1L, new DocumentTypeForm("X", null, true)), EEmployeeError.EMP_021);
        assertError(() -> service.delete(1L), EEmployeeError.EMP_021);
    }

    @Test
    void typeInUseCannotBeDeleted() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(types.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(type(9L, 10L, false)));
        when(documents.existsByDocumentTypeId(9L)).thenReturn(true);
        assertError(() -> service.delete(9L), EEmployeeError.EMP_010);
    }

    @Test
    void otherCompanyTypeIsNotFound() {
        TestLogin.as(11L, "ORG_MANAGE");
        when(types.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(type(9L, 10L, false)));
        assertError(() -> service.setRequired(null, 9L, true), EEmployeeError.EMP_005);
    }

    @Test
    void updateIsFullReplacement() {
        TestLogin.as(10L, "ORG_MANAGE");
        DocumentType t = type(9L, 10L, true);
        t.setDescription("old");
        when(types.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(t));
        var d = service.update(9L, new DocumentTypeForm(" Permit ", null, false));
        assertThat(d.name()).isEqualTo("Permit");
        assertThat(d.description()).isNull();
        assertThat(d.required()).isFalse();
    }

    private static DocumentType type(Long id, Long companyId, boolean required) {
        DocumentType t = new DocumentType();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setName("T" + id);
        t.setRequired(required);
        return t;
    }

    private static DocumentTypeSetting setting(Long typeId, boolean required) {
        DocumentTypeSetting s = new DocumentTypeSetting();
        s.setCompanyId(10L);
        s.setDocumentTypeId(typeId);
        s.setRequired(required);
        return s;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
