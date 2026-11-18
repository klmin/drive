package com.thinkfree.drive.api.v1.notification.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.notification.request.NotificationListRequest;
import com.thinkfree.drive.api.v1.notification.response.NotificationListResponse;
import com.thinkfree.drive.api.v1.notification.response.NotificationReadResponse;
import com.thinkfree.drive.api.v1.notification.usecase.NotificationUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    
    private final NotificationUseCase notificationUseCase;
    
    @ExternalApi
    @Operation(summary = "List notifications",
            description = "Returns a page of notifications for the signed-in user using the supplied filters.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationListResponse>>> findAllPaginated(@ModelAttribute NotificationListRequest request, @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                        @AuthenticationPrincipal CustomUserDetails details) {

        var response = notificationUseCase.findAllCursorPaginated(request, pagingRequest, details.getUserSeq());
        return ApiResponse.successWithCursor(response.getList(), response.getPage());

    }

    @ExternalApi
    @Operation(summary = "Count unread notifications",
            description = "Returns the number of unread notifications for the signed-in user.")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> countUnread(@AuthenticationPrincipal CustomUserDetails userDetails) {
        var response = new NotificationReadResponse(notificationUseCase.countUnread(userDetails.getUserSeq()));
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Mark all notifications as read",
            description = "Marks the signed-in user's unread notifications as read.")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationUseCase.markAllAsRead(userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Mark a notification as read",
            description = "Marks the selected notification as read and returns the updated unread count.")
    @PatchMapping("/{notificationSeq}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsReadAndGetUnreadCount(@ParameterDoc(title = "Notification ID", description = "Numeric notificationSeq returned by the notification list.") @PathVariable Long notificationSeq, @AuthenticationPrincipal CustomUserDetails userDetails) {
        var response = notificationUseCase.markAsReadAndGetUnreadCount(notificationSeq, userDetails.getUserSeq());
        return ApiResponse.success(response);
    }
    
    @ExternalApi
    @Operation(summary = "Mark a notification as unread",
            description = "Marks the selected notification as unread and returns the updated unread count.")
    @PatchMapping("/{notificationSeq}/unread")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsUnreadAndGetUnreadCount(@ParameterDoc(title = "Notification ID", description = "Numeric notificationSeq returned by the notification list.") @PathVariable Long notificationSeq, @AuthenticationPrincipal CustomUserDetails userDetails) {
        var response = notificationUseCase.markAsUnreadAndGetUnreadCount(notificationSeq, userDetails.getUserSeq());
        return ApiResponse.success(response);
    }
    
}
