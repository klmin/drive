package com.thinkfree.drive.notification.data;

import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(NotificationJpaRepository#findList)
public class NotificationData {
    private Long notificationSeq;
    private Integer sharedByUserSeq;
    private ResourceType resourceType;
    private Long resourceSeq;
    private String resourceName;
    private String ext;
    private ActionType actionType;
    private String description;
    private Boolean isRead;
    private Integer registerUserSeq;
    private Instant registerDate;
}
