package com.terp.erp.notification.feed;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.notification.feed.FeedService.FeedItemDto;
import com.terp.erp.notification.feed.FeedService.ReadAllDto;
import com.terp.erp.notification.feed.FeedService.UnreadDto;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The caller's own notifications: no permission beyond being a signed-in user of a company. */
@Tag(name = "My notifications")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/me")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {
    private final FeedService service;
    private final FeedStreamService streams;

    @Operation(summary = "My notification feed", description = "Newest first by default; unreadOnly=true keeps the "
            + "unread rows only.")
    @ApiResponses({@ApiResponse(responseCode = "403", description = "Not a user of a company (NTF_007)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiMessageDto<ResponseListDto<FeedItemDto>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(unreadOnly, pageable)));
    }

    @Operation(summary = "My unread notification count")
    @ApiResponses({@ApiResponse(responseCode = "403", description = "Not a user of a company (NTF_007)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread-count")
    public ApiMessageDto<UnreadDto> unreadCount() {
        return ApiMessageDto.ok(service.unreadCount());
    }

    @Operation(summary = "Mark one of my notifications read", description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Not a user of a company (NTF_007)"),
            @ApiResponse(responseCode = "404", description = "Not one of the caller's notifications (NTF_006)")})
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/read")
    public ApiMessageDto<FeedItemDto> read(@PathVariable Long id) {
        return ApiMessageDto.ok(service.markRead(id));
    }

    @Operation(summary = "Mark all my notifications read", description = "Answers how many changed.")
    @ApiResponses({@ApiResponse(responseCode = "403", description = "Not a user of a company (NTF_007)")})
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/read-all")
    public ApiMessageDto<ReadAllDto> readAll() {
        return ApiMessageDto.ok(service.markAllRead());
    }

    @Operation(summary = "Live stream of my notifications (Server-Sent Events)", description = "text/event-stream. "
            + "Events: notification {id, eventType, title, linkType, linkId, createdAt}; unread {count} (also once on "
            + "connect); chat {fromUserId, messageId} (refresh the messenger, no message body); a ':hb' comment every "
            + "20 s. Send the bearer token in the Authorization header (fetch-based client: the browser EventSource "
            + "cannot send headers; a token in the query string is not accepted). The stream ends when the token "
            + "expires (at most 30 min): reconnect with a fresh token. At most 5 streams per user; a 6th closes the "
            + "oldest.")
    @ApiResponses({@ApiResponse(responseCode = "403", description = "Not a user of a company (NTF_007)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streams.open();
    }
}
