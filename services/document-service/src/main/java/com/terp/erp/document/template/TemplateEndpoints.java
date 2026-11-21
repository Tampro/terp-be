package com.terp.erp.document.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.template.DocTemplate.EKind;
import com.terp.erp.document.template.DocTemplateService.Generated;
import com.terp.erp.document.template.DocTemplateService.Preview;
import com.terp.erp.document.template.DocTemplateService.StatusCounts;
import com.terp.erp.document.template.DocTemplateService.TemplateDto;
import com.terp.erp.document.template.DocTemplateService.TemplateForm;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The shared handlers of the two template controllers (the kind and the permission differ). */
@RequiredArgsConstructor
abstract class TemplateEndpoints {
    static final String NO_COMPANY = "Super admin without companyId (DOC_001)";
    static final String NOT_FOUND = "No such template in your company (DOC_019)";
    static final String FORM_ERRORS = "Bad variable name (DOC_022)";

    private final DocTemplateService service;
    private final EKind kind;

    public record StatusForm(@NotNull EStatus status) {
    }

    /** values = the variables to fill. */
    public record GenerateForm(@Size(max = 200) Map<String, @Size(max = 10000) String> values) {
    }

    ApiMessageDto<ResponseListDto<TemplateDto>> doList(Long companyId, String keyword, Long groupId, EStatus status,
                                                       Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(kind, companyId, keyword, groupId, status, pageable)));
    }

    ApiMessageDto<StatusCounts> doCounts(Long companyId, String keyword, Long groupId) {
        return ApiMessageDto.ok(service.statusCounts(kind, companyId, keyword, groupId));
    }

    ApiMessageDto<TemplateDto> doGet(Long id) {
        return ApiMessageDto.ok(service.get(kind, id));
    }

    ApiMessageDto<Preview> doPreview(Long id) {
        return ApiMessageDto.ok(service.preview(kind, id));
    }

    ApiMessageDto<TemplateDto> doCreate(Long companyId, @Valid TemplateForm form) {
        return ApiMessageDto.ok(service.create(kind, companyId, form));
    }

    ApiMessageDto<TemplateDto> doUpdate(Long id, @Valid TemplateForm form) {
        return ApiMessageDto.ok(service.update(kind, id, form));
    }

    ApiMessageDto<TemplateDto> doStatus(Long id, EStatus status) {
        return ApiMessageDto.ok(service.changeStatus(kind, id, status));
    }

    ApiMessageDto<Void> doDelete(Long id) {
        service.delete(kind, id);
        return ApiMessageDto.ok(null);
    }

    ResponseEntity<byte[]> doGenerate(Long id, GenerateForm form, Long contractId) {
        Generated g = service.generate(kind, id, form == null ? null : form.values(), contractId);
        ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(g.fileName(), StandardCharsets.UTF_8).build().toString());
        if (g.attachedFileId() != null) {
            b.header("X-Attached-File-Id", g.attachedFileId());
        }
        return b.body(g.pdf());
    }
}
