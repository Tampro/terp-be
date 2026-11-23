package com.terp.erp.iam.integration;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.integration.ChannelEventService.ChannelEventsForm;
import com.terp.erp.iam.integration.ChannelEventService.ChannelEventsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Company integrations", description = "Per-event switches of Slack, Telegram and SMS (Twilio).")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/integrations/{channel}/events")
@SecurityRequirement(name = "bearerAuth")
public class ChannelEventController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String CHANNEL = "slack, telegram or sms";
    private static final String ERRORS = "Unknown channel (SETTINGS_020), super admin without companyId (IAM_003)";

    private final ChannelEventService service;

    @Operation(summary = "Routable events with the channel's switch, grouped by module",
            description = "A company that never saved the list has every event on (configured=false).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<ChannelEventsView> get(@Parameter(description = CHANNEL) @PathVariable String channel,
                                                @Parameter(description = COMPANY) @RequestParam(required = false)
                                                Long companyId) {
        return ApiMessageDto.ok(service.get(companyId, channel));
    }

    @Operation(summary = "Replace the events the channel fires for",
            description = "Full replacement: the listed event types are on, every other catalog event is off.")
    @ApiResponses(@ApiResponse(responseCode = "400",
            description = "Unknown event type (SETTINGS_021), validation, " + ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping
    public ApiMessageDto<ChannelEventsView> replace(@Parameter(description = CHANNEL) @PathVariable String channel,
                                                    @Parameter(description = COMPANY) @RequestParam(required = false)
                                                    Long companyId,
                                                    @Valid @RequestBody ChannelEventsForm form) {
        return ApiMessageDto.ok(service.replace(companyId, channel, form));
    }
}
