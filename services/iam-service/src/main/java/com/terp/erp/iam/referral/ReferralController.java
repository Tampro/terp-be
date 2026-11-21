package com.terp.erp.iam.referral;

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
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.terp.erp.iam.referral.ReferralDtos.DashboardDto;
import com.terp.erp.iam.referral.ReferralDtos.MyReferralDto;
import com.terp.erp.iam.referral.ReferralDtos.PayoutCountsDto;
import com.terp.erp.iam.referral.ReferralDtos.PayoutDecisionForm;
import com.terp.erp.iam.referral.ReferralDtos.PayoutDto;
import com.terp.erp.iam.referral.ReferralDtos.PayoutForm;
import com.terp.erp.iam.referral.ReferralDtos.ReferredCompanyDto;
import com.terp.erp.iam.referral.ReferralDtos.ReferredSummaryDto;
import com.terp.erp.iam.referral.ReferralDtos.SettingsDto;
import com.terp.erp.iam.referral.ReferralDtos.SettingsForm;

/** Referral program (docs/analysis/06 §3.2.6). Amounts are in the default currency. */
@Tag(name = "SaaS — referral program")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saas")
@SecurityRequirement(name = "bearerAuth")
public class ReferralController {
    private final ReferralService referral;

    // ------------------------------------------------------------------ company side

    @Operation(summary = "My referral: code to share, program rules, earned / paid / pending / available")
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_VIEW')")
    @GetMapping("/referral")
    public ApiMessageDto<MyReferralDto> mine(@Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(referral.mine(companyId));
    }

    @Operation(summary = "My payout requests")
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_VIEW')")
    @GetMapping("/referral/payouts")
    public ApiMessageDto<ResponseListDto<PayoutDto>> myPayouts(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(referral.myPayouts(companyId, pageable)));
    }

    @Operation(summary = "Request a payout of earned commission")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Program disabled (SAAS_032), pending request "
            + "exists (SAAS_029), below threshold (SAAS_027), above available balance (SAAS_028)"))
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/referral/payouts")
    public ApiMessageDto<PayoutDto> requestPayout(@Valid @RequestBody PayoutForm form) {
        return ApiMessageDto.ok(referral.requestPayout(form.amount()));
    }

    // ------------------------------------------------------------------ super admin

    @Operation(summary = "Dashboard: cards, top referring companies, this year's signups and payouts")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/dashboard")
    public ApiMessageDto<DashboardDto> dashboard() {
        return ApiMessageDto.ok(referral.dashboard());
    }

    @Operation(summary = "Referred Users summary cards")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/referred/summary")
    public ApiMessageDto<ReferredSummaryDto> referredSummary() {
        return ApiMessageDto.ok(referral.referredSummary());
    }

    @Operation(summary = "Referred Users list", description = "keyword matches company name or email. Each row: "
            + "registration date, referrer, current plan and its monthly price, commission total and history.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/referred")
    public ApiMessageDto<ResponseListDto<ReferredCompanyDto>> referred(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(referral.referred(keyword, pageable)));
    }

    @Operation(summary = "Payout Requests", description = "keyword matches company name or email; status = tab.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/payouts")
    public ApiMessageDto<ResponseListDto<PayoutDto>> payouts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EPayoutStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(referral.searchPayouts(keyword, status, pageable)));
    }

    @Operation(summary = "Counts for the payout status tabs")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/payouts/status-counts")
    public ApiMessageDto<PayoutCountsDto> payoutCounts(@RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(referral.payoutCounts(keyword));
    }

    @Operation(summary = "Approve a payout (payment made outside the system)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_031)"),
            @ApiResponse(responseCode = "404", description = "No such payout request (SAAS_030)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/admin/referral/payouts/{id}/approve")
    public ApiMessageDto<PayoutDto> approve(@PathVariable Long id, @Valid @RequestBody(required = false) PayoutDecisionForm form) {
        return ApiMessageDto.ok(referral.decide(id, true, form == null ? null : form.notes()));
    }

    @Operation(summary = "Reject a payout (the amount returns to the available balance)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_031)"),
            @ApiResponse(responseCode = "404", description = "No such payout request (SAAS_030)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/admin/referral/payouts/{id}/reject")
    public ApiMessageDto<PayoutDto> reject(@PathVariable Long id, @Valid @RequestBody(required = false) PayoutDecisionForm form) {
        return ApiMessageDto.ok(referral.decide(id, false, form == null ? null : form.notes()));
    }

    @Operation(summary = "Referral settings")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/referral/settings")
    public ApiMessageDto<SettingsDto> settings() {
        return ApiMessageDto.ok(referral.settings());
    }

    @Operation(summary = "Save referral settings (full replacement)", description = "A new percentage applies to "
            + "orders approved from now on; earned commission keeps its percentage.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/referral/settings")
    public ApiMessageDto<SettingsDto> saveSettings(@Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(referral.saveSettings(form));
    }
}
