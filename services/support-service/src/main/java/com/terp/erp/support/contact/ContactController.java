package com.terp.erp.support.contact;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.contact.ContactService.ContactDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/contacts")
@Tag(name = "Contact messages", description = "Messages sent from the support portal contact form (ERP manual 18)")
@SecurityRequirement(name = "bearerAuth")
public class ContactController {
    private final ContactService service;

    @GetMapping
    @PreAuthorize("@PermissionChecker.has('SUPPORT_CONTACT_VIEW')")
    @Operation(summary = "List contact messages, newest first", description = "keyword matches name, email, subject.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<ResponseListDto<ContactDto>> list(
            @RequestParam(required = false) Long companyId, @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, keyword, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_CONTACT_VIEW')")
    @Operation(summary = "A contact message")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_025")})
    public ApiMessageDto<ContactDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_CONTACT_MANAGE')")
    @Operation(summary = "Delete a contact message (soft)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_025")})
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
