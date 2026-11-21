package com.terp.erp.recruitment.question;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;

/** Custom Questions page (docs/analysis/06 §7.1.17). Postings pick which ones they ask (next PR). */
@Service
@RequiredArgsConstructor
public class CustomQuestionService {
    private final CustomQuestionRepository questions;

    public record QuestionDto(String id, String question, boolean required, Instant createdDate, Instant updatedDate) {
        static QuestionDto of(CustomQuestion q) {
            return new QuestionDto(q.getId().toString(), q.getQuestion(), q.isRequired(), q.getCreatedDate(),
                    q.getUpdatedDate());
        }
    }

    public record QuestionForm(
            @Schema(example = "Why do you want to join us?", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000) String question,
            @Schema(description = "Mandatory for applicants") boolean required) {
    }

    @Transactional(readOnly = true)
    public Page<QuestionDto> search(Long requestedCompanyId, String keyword, Pageable pageable) {
        return questions.search(CatalogService.company(requestedCompanyId), Texts.kw(keyword), pageable)
                .map(QuestionDto::of);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> options(Long requestedCompanyId) {
        return questions.findByCompanyIdAndDeletedFalseOrderByIdAsc(CatalogService.company(requestedCompanyId)).stream()
                .map(QuestionDto::of).toList();
    }

    @Transactional(readOnly = true)
    public QuestionDto get(Long id) {
        return QuestionDto.of(require(id));
    }

    @Transactional
    public QuestionDto create(Long requestedCompanyId, QuestionForm form) {
        CustomQuestion q = new CustomQuestion();
        q.setCompanyId(CatalogService.company(requestedCompanyId));
        apply(q, form);
        return QuestionDto.of(questions.save(q));
    }

    /** Full replacement. */
    @Transactional
    public QuestionDto update(Long id, QuestionForm form) {
        CustomQuestion q = require(id);
        apply(q, form);
        return QuestionDto.of(q);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private static void apply(CustomQuestion q, QuestionForm f) {
        q.setQuestion(f.question().trim());
        q.setRequired(f.required());
    }

    public CustomQuestion require(Long id) {
        CustomQuestion q = questions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_003));
        TenantGuard.assertSameCompany(q.getCompanyId(), ERecruitmentError.REC_003);
        return q;
    }
}
