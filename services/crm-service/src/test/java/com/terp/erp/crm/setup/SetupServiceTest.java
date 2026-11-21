package com.terp.erp.crm.setup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmLinkRepository;
import com.terp.erp.crm.record.DealRepository;
import com.terp.erp.crm.record.LeadRepository;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetupServiceTest {
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final StageRepository stages = mock(StageRepository.class);
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final SetupService service = new SetupService(pipelines, stages, mock(LabelRepository.class),
            mock(SourceRepository.class), leads, deals, mock(CrmLinkRepository.class));
    private final Pipeline pipeline = new Pipeline();

    @BeforeEach
    void setUp() {
        pipeline.setId(1L);
        pipeline.setCompanyId(10L);
        pipeline.setName("Marketing");
        when(pipelines.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(pipeline));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reorderTakesExactlyTheStagesOfThatKind() {
        TestLogin.as(10L, "CRM_MANAGE");
        Stage a = stage(11L, 1), b = stage(12L, 2);
        when(stages.findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(1L, EStageKind.LEAD))
                .thenReturn(List.of(a, b));

        service.reorder(1L, new SetupService.StageOrderForm(EStageKind.LEAD, List.of(12L, 11L)));
        assertThat(b.getPosition()).isEqualTo(1);
        assertThat(a.getPosition()).isEqualTo(2);
        assertThatThrownBy(() -> service.reorder(1L, new SetupService.StageOrderForm(EStageKind.LEAD, List.of(12L))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_020);
    }

    @Test
    void aStageOrPipelineInUseCannotBeDeleted() {
        TestLogin.as(10L, "CRM_MANAGE");
        Stage a = stage(11L, 1);
        when(stages.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(a));
        when(leads.existsByStageIdAndDeletedFalse(11L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteStage(11L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_009);
        when(deals.existsByPipelineIdAndDeletedFalse(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deletePipeline(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_009);
    }

    @Test
    void pipelineOfAnotherCompanyIsNotFoundAndAPipelineWithoutStagesTakesNothing() {
        TestLogin.as(20L, "CRM_VIEW");
        assertThatThrownBy(() -> service.requirePipeline(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_002);
        when(stages.findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(1L, EStageKind.DEAL)).thenReturn(List.of());
        assertThatThrownBy(() -> service.firstStage(1L, EStageKind.DEAL))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_010);
    }

    private static Stage stage(Long id, int position) {
        Stage s = new Stage();
        s.setId(id);
        s.setCompanyId(10L);
        s.setPipelineId(1L);
        s.setKind(EStageKind.LEAD);
        s.setName("Stage " + id);
        s.setPosition(position);
        return s;
    }
}
