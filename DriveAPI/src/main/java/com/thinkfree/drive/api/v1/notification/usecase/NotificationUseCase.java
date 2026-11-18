package com.thinkfree.drive.api.v1.notification.usecase;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.page.response.CursorPaginationResponse;
import com.thinkfree.drive.api.v1.notification.converter.NotificationRequestConverter;
import com.thinkfree.drive.api.v1.notification.converter.NotificationResponseConverter;
import com.thinkfree.drive.api.v1.notification.request.NotificationListRequest;
import com.thinkfree.drive.api.v1.notification.response.NotificationListResponse;
import com.thinkfree.drive.api.v1.notification.response.NotificationReadResponse;
import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.service.NotificationFindService;
import com.thinkfree.drive.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationUseCase {
    
    private final NotificationRequestConverter notificationRequestConverter;
    private final NotificationResponseConverter notificationResponseConverter;
    private final NotificationFindService notificationFindService;
    private final NotificationService notificationService;
    
    public CursorPaginationResponse<NotificationListResponse> findAllCursorPaginated(NotificationListRequest request, CursorPagingRequest pagingRequest, int userSeq){
        var notificationListQuery = notificationRequestConverter.toNotificationListQuery(request, pagingRequest, userSeq);
        var notifications = notificationFindService.findAll(notificationListQuery);
        var response = notificationResponseConverter.toNotificationListResponse(notifications);
        var page = CursorApiPage.of(notifications, pagingRequest.ps(), null, NotificationData::getNotificationSeq, null);
        return CursorPaginationResponse.of(response, page);
     
    }
    
    public int countUnread(int userSeq){
        return notificationService.countUnread(userSeq).unreadCount();
    }

    public void markAllAsRead(int userSeq){
        var notificationAllReadDto = notificationRequestConverter.toNotificationAllReadDto(userSeq, true, Instant.now());
        notificationService.markAllAsRead(notificationAllReadDto);
    }
    
    public NotificationReadResponse markAsReadAndGetUnreadCount(Long notificationSeq, int userSeq){
        var notificationReadDto = notificationRequestConverter.toMarkReadDto(notificationSeq, userSeq);
        var result = notificationService.applyReadStateAndCountUnread(notificationReadDto);
        return new NotificationReadResponse(result.unreadCount());
    }
    
    public NotificationReadResponse markAsUnreadAndGetUnreadCount(Long notificationSeq, int userSeq){
        var notificationReadDto = notificationRequestConverter.toMarkUnreadDto(notificationSeq, userSeq);
        var result = notificationService.applyReadStateAndCountUnread(notificationReadDto);
        return new NotificationReadResponse(result.unreadCount());
    }
}
