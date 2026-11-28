package com.terp.erp.iam.internal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.DeliveryPermissions;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Service-to-service only (SecurityConfig internalChain, erp.iam.internal-client-ids): delivery-service checks a rider
 * before assigning a delivery. canRide = one of the user's live roles grants DELIVERY_RIDE (plan modules aside:
 * DELIVERY is always on).
 */
@Tag(name = "Internal")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/companies")
@SecurityRequirement(name = "bearerAuth")
public class InternalRiderController {
    private final UserAccountRepository users;

    public record RiderDto(String userId, String keycloakSub, String fullName, boolean active, boolean canRide) {
    }

    @Operation(summary = "Can this user deliver for the company? (delivery-service)",
            description = "active = account ACTIVE; canRide = a role of the user grants DELIVERY_RIDE.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "No or invalid token (COMMON_003)"),
            @ApiResponse(responseCode = "403", description = "Token not of an allowed service account (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such user in the company (IAM_009)")})
    @Transactional(readOnly = true)
    @GetMapping("/{companyId}/riders/{userId}")
    public ApiMessageDto<RiderDto> rider(@PathVariable Long companyId, @PathVariable Long userId) {
        UserAccount u = users.findByIdAndDeletedFalse(userId)
                .filter(x -> Objects.equals(x.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_009));
        return ApiMessageDto.ok(of(u));
    }

    static RiderDto of(UserAccount u) {
        boolean canRide = u.getRoles().stream().filter(r -> !r.isDeleted()).map(Role::getPermissions)
                .flatMap(java.util.Collection::stream)
                .anyMatch(p -> DeliveryPermissions.DELIVERY_RIDE.equals(p.getCode()));
        return new RiderDto(String.valueOf(u.getId()), u.getKeycloakId(), u.getFullName(),
                u.getStatus() == EUserStatus.ACTIVE, canRide);
    }
}
