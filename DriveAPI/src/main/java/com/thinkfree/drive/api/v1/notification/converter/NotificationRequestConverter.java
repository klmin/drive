package com.thinkfree.drive.api.v1.notification.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.notification.request.NotificationListRequest;
import com.thinkfree.drive.notification.dto.NotificationAllReadDto;
import com.thinkfree.drive.notification.dto.NotificationReadDto;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class})
public interface NotificationRequestConverter {
    
    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(pagingRequest.cv(), pagingRequest.ci(), pagingRequest.ct(), pagingRequest.ps()))")
    @Mapping(target = "isRead", source = "request.ir")
    NotificationListQuery toNotificationListQuery(NotificationListRequest request, CursorPagingRequest pagingRequest, int userSeq);
    
    NotificationAllReadDto toNotificationAllReadDto(Integer userSeq, boolean isRead, Instant readDatetime);
    
    @Mapping(target = "isRead", constant = "true")
    @Mapping(target = "readDatetime", expression = "java(java.time.Instant.now())")
    NotificationReadDto toMarkReadDto(Long notificationSeq, int userSeq);
    
    @Mapping(target = "isRead", constant = "false")
    @Mapping(target = "readDatetime", ignore = true)
    NotificationReadDto toMarkUnreadDto(Long notificationSeq, int userSeq);
}
