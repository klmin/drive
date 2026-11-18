package com.thinkfree.drive.notification.service.impl;

import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.notification.command.EventWithNotifications;
import com.thinkfree.drive.notification.command.NotificationWithEventBatchCommand;
import com.thinkfree.drive.notification.converter.NotificationConverter;
import com.thinkfree.drive.notification.dto.NotificationAllReadDto;
import com.thinkfree.drive.notification.dto.NotificationReadDto;
import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.event.converter.NotificationEventConverter;
import com.thinkfree.drive.notification.event.service.NotificationEventService;
import com.thinkfree.drive.notification.exception.NotificationErrorCode;
import com.thinkfree.drive.notification.planner.NotificationPlannerRegistry;
import com.thinkfree.drive.notification.repository.NotificationRepository;
import com.thinkfree.drive.notification.result.NotificationUnreadResult;
import com.thinkfree.drive.notification.service.NotificationFindService;
import com.thinkfree.drive.notification.service.NotificationService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultNotificationService implements NotificationService {
    
    private final NotificationConverter notificationConverter;
    private final NotificationEventConverter notificationEventConverter;
    private final NotificationRepository notificationRepository;
    private final NotificationPlannerRegistry notificationPlannerRegistry;
    private final NotificationEventService notificationEventService;
    private final NotificationFindService notificationFindService;
    
    @Override
    public void saveNotificationWithEvent(NotificationWithEventSaveDto dto) {
        
        var planner = notificationPlannerRegistry.get(dto.getActionType());
        var plannerDto = notificationConverter.toNotificationPlannerDto(dto);
        var plannerResult = planner.plan(plannerDto);
        
        if (!dto.getActionType().getPayloadClass().isInstance(plannerResult.payload())) {
            throw new IllegalArgumentException("Payload type mismatch for " + dto.getActionType());
        }
        
        if (plannerResult.recipients() == null || plannerResult.recipients().isEmpty()){
            return;
        }
        
        var notificationEventSaveDto = notificationEventConverter.notificationEventToSaveDto(dto, plannerResult.payload());
        var notificationEventSeq = notificationEventService.save(notificationEventSaveDto);
        var entities = plannerResult.recipients()
                                    .stream()
                                    .map(d -> notificationConverter.toSaveNotification(d, notificationEventSeq, dto.getActorUserSeq()))
                                    .toList();
        
        saveAll(entities);
        
    }
    
    @Override
    public void saveNotificationWithEventBatch(List<NotificationWithEventSaveDto> list) {
        
        if(list == null || list.isEmpty()){
            return;
        }
        
        var actionType = list.get(0).getActionType();
        var planner = notificationPlannerRegistry.get(actionType);
        
        List<EventWithNotifications> eventWithNotificationsList = new ArrayList<>();
        
        for(var dto : list){
            
            var plannerDto = notificationConverter.toNotificationPlannerDto(dto);
            var plannerResult = planner.plan(plannerDto);

            if (!actionType.getPayloadClass().isInstance(plannerResult.payload())) {
                throw new IllegalArgumentException("Payload type mismatch for " + actionType);
            }

            if (plannerResult.recipients() == null || plannerResult.recipients().isEmpty()){
                continue;
            }
            
            var notificationEventSaveDto = notificationEventConverter.notificationEventToSaveDto(dto, plannerResult.payload());
            var notificationEvent = notificationEventConverter.toSaveNotification(notificationEventSaveDto);
            var entities = plannerResult.recipients()
                                    .stream()
                                    .map(d -> notificationConverter.toSaveNotification(d, null, dto.getActorUserSeq()))
                                    .toList();
            
            eventWithNotificationsList.add(new EventWithNotifications(notificationEvent, entities));
        }
        
        NotificationWithEventBatchCommand notificationWithEventBatchCommand = new NotificationWithEventBatchCommand(eventWithNotificationsList);
        notificationRepository.saveNotificationWithEventBatch2Phase(notificationWithEventBatchCommand);
    
    }
    
    
    @Override
    public void markAllAsRead(NotificationAllReadDto dto) {
        var result = notificationRepository.updateAllReadState(dto.getUserSeq(), dto.getIsRead(), dto.getReadDatetime());
        log.info("모두 읽음 건수 : {}, userSeq : {}", result, dto.getUserSeq());
    }

    /** 관리 엔티티를 고쳐 커밋 때 변경 감지 — 바로 뒤의 안 읽은 수 count(JPQL)는 실행 직전 자동 flush 로 바뀐 값을 센다 */
    private void updateReadState(NotificationReadDto dto, ErrorCode errorCode){
        var notification = notificationRepository.findByUserSeqAndNotificationSeq(dto.getUserSeq(), dto.getNotificationSeq());
        DriveAssert.throwIfNull(notification, errorCode);
        notification.changeReadState(dto.getIsRead(), dto.getReadDatetime());
    }
    
    @Override
    public NotificationUnreadResult applyReadStateAndCountUnread(NotificationReadDto dto) {
        ErrorCode errorCode = dto.getIsRead() ? NotificationErrorCode.READ_FAIL : NotificationErrorCode.UNREAD_FAIL;
        updateReadState(dto, errorCode);
        return countUnread(dto.getUserSeq());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadResult countUnread(int userSeq) {
        var notificationReadStateFindQuery = notificationConverter.toNotificationReadStateFindQuery(userSeq, false);
        var findReadStateData = notificationFindService.findReadStateData(notificationReadStateFindQuery);
        return new NotificationUnreadResult(findReadStateData.getReadStateCount());
    }

    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        deleteByUserSeqList(userSeqList);
        notificationEventService.deleteBySharedByUserSeqList(userSeqList);
    }
    
    private void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = notificationRepository.deleteByUserSeqList(userSeqList);
        log.info("[알림 삭제 : {}건]", result);
    }
    
    private void saveAll(List<Notification> entities){
        notificationRepository.saveAll(entities);
    }
}
