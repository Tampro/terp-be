package com.terp.erp.meeting.type;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.type.MeetingTypeService.Option;
import com.terp.erp.meeting.type.MeetingTypeService.TypeDto;
import com.terp.erp.meeting.type.MeetingTypeService.TypeForm;

import java.util.List;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Meeting types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/types")
@SecurityRequirement(name = "bearerAuth")
public class MeetingTypeController {
    private static final String NOT_FOUND = "No such meeting type in your company (MTG_002)";

    private final MeetingTypeService service;

    public record StatusForm(@NotNull EStatus status) {
    }

    @Operation(summary = "Meeting types of the company", description = "keyword = name or description; status = the "
            + "status filter.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (MTG_001)"))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Meeting type dropdown", description = "Active types by name, with the default duration.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (MTG_001)"))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Meeting type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add New Meeting Type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (MTG_003), super admin "
            + "without companyId (MTG_001)"))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a meeting type", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (MTG_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Deactivate / activate a meeting type")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TypeDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a meeting type", description = "Only a type without meetings; deactivate otherwise.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The type has meetings (MTG_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
