package com.terp.erp.support.messenger;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.messenger.MessengerService.ConversationDto;
import com.terp.erp.support.messenger.MessengerService.FavoriteDto;
import com.terp.erp.support.messenger.MessengerService.MessageDto;
import com.terp.erp.support.messenger.MessengerService.ReadResult;
import com.terp.erp.support.messenger.MessengerService.SendForm;
import com.terp.erp.support.messenger.MessengerService.UnreadDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messenger")
@Tag(name = "Messenger", description = "One-to-one chat between users of the same company (ERP manual 18); REST, polled")
@SecurityRequirement(name = "bearerAuth")
public class MessengerController {
    private static final String NO_ACCOUNT = "SUP_028 (super admin or no company account)";

    private final MessengerService service;

    @GetMapping("/conversations")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "My conversations: the other user, last message, unread count, favorite; latest first")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<ResponseListDto<ConversationDto>> conversations(@ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.conversations(pageable)));
    }

    @GetMapping("/conversations/{userId}/messages")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "My messages with a user, newest first (sort is fixed)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<ResponseListDto<MessageDto>> messages(@PathVariable Long userId,
                                                               @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.messages(userId, pageable)));
    }

    @PostMapping("/conversations/{userId}/messages")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "Send a message (text and/or one media file of the company)",
            description = "The receiver must be an active user of my company; IAM is asked with my token "
                    + "(GET /api/v1/iam/colleagues/{id}, MESSENGER_USE).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_007, SUP_029, SUP_030, SUP_031"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT + "; COMMON_004 relayed from IAM"),
            @ApiResponse(responseCode = "503", description = "SUP_002, SUP_003")})
    public ApiMessageDto<MessageDto> send(@PathVariable Long userId, @Valid @RequestBody SendForm form) {
        return ApiMessageDto.ok(service.send(userId, form));
    }

    @PostMapping("/conversations/{userId}/read")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "Mark every message from this user to me as read")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<ReadResult> markRead(@PathVariable Long userId) {
        return ApiMessageDto.ok(service.markRead(userId));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "Unread messages to me in total (badge)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<UnreadDto> unread() {
        return ApiMessageDto.ok(service.unread());
    }

    @GetMapping("/favorites")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "My favorite contacts, latest added first")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<List<FavoriteDto>> favorites() {
        return ApiMessageDto.ok(service.favorites());
    }

    @PutMapping("/favorites/{userId}")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "Add a favorite contact (idempotent)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_029, SUP_030"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT + "; COMMON_004 relayed from IAM"),
            @ApiResponse(responseCode = "503", description = "SUP_003")})
    public ApiMessageDto<FavoriteDto> addFavorite(@PathVariable Long userId) {
        return ApiMessageDto.ok(service.addFavorite(userId));
    }

    @DeleteMapping("/favorites/{userId}")
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @Operation(summary = "Remove a favorite contact (idempotent)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = NO_ACCOUNT)})
    public ApiMessageDto<Void> removeFavorite(@PathVariable Long userId) {
        service.removeFavorite(userId);
        return ApiMessageDto.ok(null);
    }
}
