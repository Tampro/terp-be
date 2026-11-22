package com.terp.erp.recruitment.question;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.question.CustomQuestion.EType;
import com.terp.erp.recruitment.question.CustomQuestionService.QuestionForm;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomQuestionServiceTest {
    private final CustomQuestionRepository questions = mock(CustomQuestionRepository.class);
    private final CustomQuestionService service = new CustomQuestionService(questions);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void typeAndOptions() {
        CustomQuestion q = new CustomQuestion();
        CustomQuestionService.apply(q, new QuestionForm("Why us?", null, null, true));
        assertThat(q.getType()).isEqualTo(EType.TEXT);
        CustomQuestionService.apply(q, new QuestionForm("Stack?", EType.CHECKBOX, List.of(" Java ", "Go", "Java", " "),
                false));
        assertThat(q.getOptions()).containsExactly("Java", "Go");
        for (EType choice : List.of(EType.SELECT, EType.RADIO, EType.CHECKBOX)) {
            assertCode(() -> CustomQuestionService.apply(q, new QuestionForm("x", choice, List.of(), false)),
                    ERecruitmentError.REC_062);
        }
        for (EType plain : List.of(EType.TEXT, EType.TEXTAREA, EType.NUMBER, EType.DATE)) {
            assertCode(() -> CustomQuestionService.apply(q, new QuestionForm("x", plain, List.of("a"), false)),
                    ERecruitmentError.REC_063);
        }
        assertThat(EType.values()).hasSize(7);
    }

    @Test
    void otherCompanyIsNotFound() {
        TestLogin.as(11L, "RECRUITMENT_MANAGE");
        CustomQuestion q = new CustomQuestion();
        q.setId(1L);
        q.setCompanyId(10L);
        when(questions.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(q));
        assertCode(() -> service.update(1L, new QuestionForm("x", EType.TEXT, null, false)), ERecruitmentError.REC_003);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
