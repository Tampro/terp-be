package com.terp.erp.crm.record;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.crm.record.ActivityService.CallDto;
import com.terp.erp.crm.record.ActivityService.CallForm;
import com.terp.erp.crm.record.ActivityService.DiscussionForm;
import com.terp.erp.crm.record.ActivityService.EmailForm;
import com.terp.erp.crm.record.ActivityService.EntryDto;
import com.terp.erp.crm.record.ActivityService.FileForm;
import com.terp.erp.crm.record.ActivityService.LinkDto;
import com.terp.erp.crm.record.ActivityService.TaskDto;
import com.terp.erp.crm.record.ActivityService.TaskForm;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.DealService.DealDetail;
import com.terp.erp.crm.record.DealService.DealForm;
import com.terp.erp.crm.record.DealService.DealRow;
import com.terp.erp.crm.record.DealService.StatusForm;
import com.terp.erp.crm.record.LeadService.ConvertForm;
import com.terp.erp.crm.record.LeadService.LeadDetail;
import com.terp.erp.crm.record.LeadService.LeadForm;
import com.terp.erp.crm.record.LeadService.LeadRow;
import com.terp.erp.crm.record.LeadService.LinksForm;
import com.terp.erp.crm.record.LeadService.MoveForm;
import com.terp.erp.crm.record.ReportService.DealReport;
import com.terp.erp.crm.record.ReportService.LeadReport;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lead, deal, activity and report controllers of the CRM. */
final class CrmControllers {
    static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    static final String LINK_KINDS = "members (account ids), labels (of the pipeline), sources, products (inventory items)"
            + "; deals also clients (accounting customers)";

    private CrmControllers() {
    }

    @Tag(name = "CRM — leads")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/crm/leads")
    @SecurityRequirement(name = "bearerAuth")
    public static class LeadController {
        private final LeadService service;

        @Operation(summary = "Leads", description = "followUpOverdue = follow-up date passed.")
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<LeadRow>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long pipelineId, @RequestParam(required = false) Long stageId,
                @RequestParam(required = false) Long ownerUserId,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpFrom,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpTo,
                @Parameter(description = "Matches name, email or subject") @RequestParam(required = false) String keyword,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, pipelineId, stageId, ownerUserId,
                    followUpFrom, followUpTo, keyword, pageable)));
        }

        @Operation(summary = "Lead board of a pipeline (one column per lead stage)")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "Pipeline (CRM_002)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/board")
        public ApiMessageDto<List<LeadService.Column>> board(@RequestParam Long pipelineId) {
            return ApiMessageDto.ok(service.board(pipelineId));
        }

        @Operation(summary = "Lead with its tabs (users, labels, sources, products, tasks, calls, discussions, emails, files, activity)")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "No such lead in your company (CRM_006)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<LeadDetail> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(id));
        }

        @Operation(summary = "Create a lead", description = "Placed last in its stage (default: the first lead stage).")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Stage not of the pipeline (CRM_008), "
                + "pipeline without lead stage (CRM_010), validation (COMMON_001)"),
                @ApiResponse(responseCode = "404", description = "Pipeline (CRM_002), stage (CRM_003)")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping
        public ApiMessageDto<LeadDetail> create(@Valid @RequestBody LeadForm form) {
            return ApiMessageDto.ok(service.create(form));
        }

        @Operation(summary = "Edit a lead", description = "Full replacement of the fields; pipelineId and stageId are ignored (use move).")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_006"))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}")
        public ApiMessageDto<LeadDetail> update(@PathVariable Long id, @Valid @RequestBody LeadForm form) {
            return ApiMessageDto.ok(service.update(id, form));
        }

        @Operation(summary = "Delete a lead")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_006"))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Move a lead to a stage / position (kanban drag)")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Stage not a lead stage of the pipeline (CRM_008)"),
                @ApiResponse(responseCode = "404", description = "CRM_006, CRM_003")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}/stage")
        public ApiMessageDto<LeadDetail> move(@PathVariable Long id, @Valid @RequestBody MoveForm form) {
            return ApiMessageDto.ok(service.move(id, form));
        }

        @Operation(summary = "Replace the users, labels, sources or products of a lead", description = LINK_KINDS)
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Label of another pipeline (CRM_004), clients on a lead (CRM_013)"),
                @ApiResponse(responseCode = "404", description = "CRM_006; label, source or item not found (CRM_004, CRM_005, INV_ relayed)")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}/{kind:members|labels|sources|products}")
        public ApiMessageDto<List<LinkDto>> links(@PathVariable Long id, @PathVariable String kind,
                                                 @Valid @RequestBody LinksForm form) {
            return ApiMessageDto.ok(service.setLinks(id, linkType(kind), form));
        }

        @Operation(summary = "Convert to a deal (once)", description = "Client: customerId (existing) or newClient "
                + "(created in accounting; needs CUSTOMER_MANAGE). copy picks what the deal takes over.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Already converted (CRM_011), client missing "
                + "or both given (CRM_018), pipeline without deal stage (CRM_010)"),
                @ApiResponse(responseCode = "404", description = "CRM_006; customer (ACC_ relayed)")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/{id}/convert")
        public ApiMessageDto<Map<String, String>> convert(@PathVariable Long id, @Valid @RequestBody ConvertForm form) {
            return ApiMessageDto.ok(service.convert(id, form));
        }
    }

    @Tag(name = "CRM — deals")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/crm/deals")
    @SecurityRequirement(name = "bearerAuth")
    public static class DealController {
        private final DealService service;

        @Operation(summary = "Deals")
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<DealRow>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long pipelineId, @RequestParam(required = false) Long stageId,
                @RequestParam(required = false) EDealStatus status,
                @Parameter(description = "Matches the deal name") @RequestParam(required = false) String keyword,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, pipelineId, stageId, status, keyword, pageable)));
        }

        @Operation(summary = "Deal board of a pipeline (columns with count and total price)")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "Pipeline (CRM_002)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/board")
        public ApiMessageDto<List<DealService.Column>> board(@RequestParam Long pipelineId) {
            return ApiMessageDto.ok(service.board(pipelineId));
        }

        @Operation(summary = "Deal with its tabs")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "No such deal in your company (CRM_007)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<DealDetail> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(id));
        }

        @Operation(summary = "Create a deal")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "CRM_008, CRM_010, validation (COMMON_001)"),
                @ApiResponse(responseCode = "404", description = "CRM_002, CRM_003; customer (ACC_ relayed)")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping
        public ApiMessageDto<DealDetail> create(@Valid @RequestBody DealForm form) {
            return ApiMessageDto.ok(service.create(form));
        }

        @Operation(summary = "Edit a deal", description = "Full replacement; clientIds replaces the clients when given.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_007"))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}")
        public ApiMessageDto<DealDetail> update(@PathVariable Long id, @Valid @RequestBody DealForm form) {
            return ApiMessageDto.ok(service.update(id, form));
        }

        @Operation(summary = "Delete a deal")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_007"))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Set the deal status: ACTIVE, WON or LOSS")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_007"))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}/status")
        public ApiMessageDto<DealDetail> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
            return ApiMessageDto.ok(service.status(id, form));
        }

        @Operation(summary = "Move a deal to a stage / position (kanban drag)")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "CRM_008"),
                @ApiResponse(responseCode = "404", description = "CRM_007, CRM_003")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}/stage")
        public ApiMessageDto<DealDetail> move(@PathVariable Long id, @Valid @RequestBody MoveForm form) {
            return ApiMessageDto.ok(service.move(id, form));
        }

        @Operation(summary = "Replace the users, clients, labels, sources or products of a deal", description = LINK_KINDS)
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Label of another pipeline (CRM_004)"),
                @ApiResponse(responseCode = "404", description = "CRM_007; CRM_004, CRM_005, INV_ / ACC_ relayed")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/{id}/{kind:members|clients|labels|sources|products}")
        public ApiMessageDto<List<LinkDto>> links(@PathVariable Long id, @PathVariable String kind,
                                                 @Valid @RequestBody LinksForm form) {
            return ApiMessageDto.ok(service.setLinks(id, linkType(kind), form));
        }
    }

    @Tag(name = "CRM — lead and deal activity")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/crm/{subject:leads|deals}/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public static class ActivityController {
        private static final String NOT_FOUND = "Lead (CRM_006) or deal (CRM_007) not in your company, or the row (CRM_013)";
        private final ActivityService service;

        @Operation(summary = "Add a task")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/tasks")
        public ApiMessageDto<TaskDto> addTask(@PathVariable String subject, @PathVariable Long id, @Valid @RequestBody TaskForm form) {
            return ApiMessageDto.ok(service.addTask(service.require(subject(subject), id), form));
        }

        @Operation(summary = "Edit a task", description = "Full replacement.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/tasks/{taskId}")
        public ApiMessageDto<TaskDto> updateTask(@PathVariable String subject, @PathVariable Long id, @PathVariable Long taskId,
                                                 @Valid @RequestBody TaskForm form) {
            return ApiMessageDto.ok(service.updateTask(service.require(subject(subject), id), taskId, form));
        }

        @Operation(summary = "Delete a task")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @DeleteMapping("/tasks/{taskId}")
        public ApiMessageDto<Void> deleteTask(@PathVariable String subject, @PathVariable Long id, @PathVariable Long taskId) {
            service.deleteTask(service.require(subject(subject), id), taskId);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Log a call")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/calls")
        public ApiMessageDto<CallDto> addCall(@PathVariable String subject, @PathVariable Long id, @Valid @RequestBody CallForm form) {
            return ApiMessageDto.ok(service.addCall(service.require(subject(subject), id), form));
        }

        @Operation(summary = "Edit a call", description = "Full replacement.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PutMapping("/calls/{callId}")
        public ApiMessageDto<CallDto> updateCall(@PathVariable String subject, @PathVariable Long id, @PathVariable Long callId,
                                                 @Valid @RequestBody CallForm form) {
            return ApiMessageDto.ok(service.updateCall(service.require(subject(subject), id), callId, form));
        }

        @Operation(summary = "Delete a call")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @DeleteMapping("/calls/{callId}")
        public ApiMessageDto<Void> deleteCall(@PathVariable String subject, @PathVariable Long id, @PathVariable Long callId) {
            service.deleteCall(service.require(subject(subject), id), callId);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Add a discussion comment")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/discussions")
        public ApiMessageDto<EntryDto> discussion(@PathVariable String subject, @PathVariable Long id,
                                                  @Valid @RequestBody DiscussionForm form) {
            return ApiMessageDto.ok(service.addDiscussion(service.require(subject(subject), id), form));
        }

        @Operation(summary = "Log an email", description = "Kept on the record; nothing is sent.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/emails")
        public ApiMessageDto<EntryDto> email(@PathVariable String subject, @PathVariable Long id, @Valid @RequestBody EmailForm form) {
            return ApiMessageDto.ok(service.addEmail(service.require(subject(subject), id), form));
        }

        @Operation(summary = "Attach a file of the media library")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a file of your company (CRM_014)"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND),
                @ApiResponse(responseCode = "503", description = "Media unavailable (CRM_016)")})
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @PostMapping("/files")
        public ApiMessageDto<EntryDto> file(@PathVariable String subject, @PathVariable Long id, @Valid @RequestBody FileForm form) {
            return ApiMessageDto.ok(service.addFile(service.require(subject(subject), id), form));
        }

        @Operation(summary = "Delete a discussion, email or file")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
        @DeleteMapping("/entries/{entryId}")
        public ApiMessageDto<Void> deleteEntry(@PathVariable String subject, @PathVariable Long id, @PathVariable Long entryId) {
            service.deleteEntry(service.require(subject(subject), id), entryId);
            return ApiMessageDto.ok(null);
        }
    }

    @Tag(name = "CRM — lead and deal activity")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/crm/tasks")
    @SecurityRequirement(name = "bearerAuth")
    public static class TaskCalendarController {
        private final TaskCalendarService service;

        @Operation(summary = "Unified calendar: lead or deal tasks of a period",
                description = "Tasks dated in [from, to] (at most 62 days) of the leads (subject=LEAD) or deals "
                        + "(subject=DEAL) of the company, by date and time; done ones included (see status).")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or longer than 62 days "
                + "(CRM_021), super admin without companyId (CRM_001)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/calendar")
        public ApiMessageDto<List<TaskCalendarService.TaskEntry>> calendar(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam ESubject subject,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            return ApiMessageDto.ok(service.calendar(companyId, subject, from, to));
        }
    }

    @Tag(name = "CRM — reports")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/crm/reports")
    @SecurityRequirement(name = "bearerAuth")
    public static class ReportController {
        private final ReportService service;

        @Operation(summary = "Lead report of a year", description = "Per month (created / converted), per source, per "
                + "user; per stage when pipelineId is given.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "Pipeline (CRM_002)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/leads")
        public ApiMessageDto<LeadReport> leads(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false) Long pipelineId,
                                               @RequestParam(required = false) Integer year) {
            return ApiMessageDto.ok(service.leads(companyId, pipelineId, year));
        }

        @Operation(summary = "Deal report of a year", description = "Per status and month (count, value), per source; "
                + "per stage when pipelineId is given.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "Pipeline (CRM_002)"))
        @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
        @GetMapping("/deals")
        public ApiMessageDto<DealReport> deals(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false) Long pipelineId,
                                               @RequestParam(required = false) Integer year) {
            return ApiMessageDto.ok(service.deals(companyId, pipelineId, year));
        }
    }

    static ESubject subject(String path) {
        return "leads".equals(path) ? ESubject.LEAD : ESubject.DEAL;
    }

    static ELinkType linkType(String kind) {
        return switch (kind) {
            case "members" -> ELinkType.MEMBER;
            case "clients" -> ELinkType.CLIENT;
            case "labels" -> ELinkType.LABEL;
            case "sources" -> ELinkType.SOURCE;
            default -> ELinkType.PRODUCT;
        };
    }
}
