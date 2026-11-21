package com.thinkfree.drive.cleanup.trash.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData;
import com.thinkfree.drive.cleanup.trash.entity.TrashCleanUpEntity;
import com.thinkfree.drive.cleanup.trash.exception.TrashCleanUpErrorCode;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import com.thinkfree.drive.cleanup.trash.repository.TrashCleanUpQueryRepository;
import com.thinkfree.drive.cleanup.trash.service.TrashCleanUpService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessTrashDto;
import com.thinkfree.drive.recent.repository.RecentRepository;
import com.thinkfree.drive.recent.query.RecentDeleteResourcesQuery;
import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceNamingLockRepository;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.query.ResourceDeleteSeqListQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionRepository;
import com.thinkfree.drive.resource.version.query.ResourceVersionDeleteSeqListQuery;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.share.query.ShareDeleteResourcesQuery;
import com.thinkfree.drive.share.target.repository.ShareTargetRepository;
import com.thinkfree.drive.share.target.query.ShareTargetDeleteResourcesQuery;
import com.thinkfree.drive.starred.repository.StarredRepository;
import com.thinkfree.drive.starred.query.StarredDeleteResourcesQuery;
import com.thinkfree.drive.trash.query.TrashDeleteResourcesQuery;
import com.thinkfree.drive.trash.repository.TrashRepository;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultTrashCleanUpService implements TrashCleanUpService {

    private final TrashCleanUpQueryRepository trashCleanUpQueryRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceVersionRepository resourceVersionRepository;
    private final ShareRepository shareRepository;
    private final ShareTargetRepository shareTargetRepository;
    private final StarredRepository starredRepository;
    private final RecentRepository recentRepository;
    private final TrashRepository trashRepository;

    private final UserService userService;
    private final ResourceNamingLockRepository userRowLock;

    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;


    private final ApplicationEventPublisher publisher;

    private final UserAuditLookupService userAuditLookupService;

    private final ResourceAuditService resourceAuditService;

    /**
     * 스케줄러 자동정리 — 대상 사용자 행을 user_seq 순으로 먼저 잠근 뒤 대상을 읽는다(휴지통 비우기와 같은 이유, {@link #cleanUpByUser} 참고).
     * 잠근 사용자만 지우고, 잠근 뒤 새로 기한이 지난 다른 사용자 항목은 다음 실행에 넘긴다.
     */
    @Override
    public void cleanUpResourcesByDate() {
        int intervalDay = DriveConstants.DEFAULT_TRASH_CLEAN_UP_INTERVAL_DAY;
        Instant threshold = Instant.now().minus(intervalDay, ChronoUnit.DAYS);
        List<Integer> userSeqs = trashCleanUpQueryRepository.findTrashUserSeqs(threshold);
        if (userSeqs.isEmpty()) {
            return;
        }
        userSeqs.stream().sorted().forEach(userRowLock::lockUser);
        var query = new TrashCleanUpByDateQuery(threshold, true, userSeqs);
        var targets = trashCleanUpQueryRepository.findTrashCleanUpTargets(query);

        // 스케줄러 자동정리(SYSTEM) - 삭제되는 리소스마다 영구삭제 감사 로그 fan-out.
        // 요청 컨텍스트가 없으므로 actor는 슈퍼어드민, tenantSeq는 대상 소유 테넌트(row별)로 override한다.
        recordPermanentDeleteAuditAsSystem(targets);

        cleanUpResources(targets);
    }

    private void recordPermanentDeleteAuditAsSystem(List<TrashCleanUpTargetsData> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        UserDetailData actor = userAuditLookupService.findUser(DriveConstants.SUPER_ADMIN_USER_SEQ);
        for (var data : targets) {
            for (var resource : data.getResourceTargets()) {
                EventName eventName = resource.getResourceType().isFile()
                        ? EventName.FILE_PERMANENT_DELETE
                        : EventName.FOLDER_PERMANENT_DELETE;
                resourceAuditService.recordPermanentDelete(eventName, resource.getResourceSeq(), resource.getResourceName(), data.getTenantSeq(), actor);
            }
        }
    }

    @Override
    public void cleanUpResourcesByUser(Integer userSeq) {
        cleanUpByUser(new TrashCleanUpByUserQuery(userSeq, true));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findTrashSeqsByUser(Integer userSeq) {
        return trashCleanUpQueryRepository.findTrashSeqs(userSeq);
    }

    @Override
    public int cleanUpResourcesByUser(Integer userSeq, List<Long> trashSeqs) {
        return cleanUpByUser(new TrashCleanUpByUserQuery(userSeq, true, trashSeqs));
    }

    /**
     * 사용자 휴지통 영구삭제 한 트랜잭션 — 지우기 전에 사용자 행을 잠그고, 잠근 뒤의 휴지통 상태로 대상을 확정한다.
     * <p>
     * 복원·휴지통 이동·영구 삭제도 사용자 행을 먼저 잠근다(소유자별 직렬화). 잠금 없이 읽은 대상을 그대로 지우면, 읽은 뒤 커밋된 복원을
     * 못 보고 방금 복원한 리소스까지 지우고 사용량도 빼게 된다.
     * <p>
     * 묶음(trashSeqs 고정): 무거운 대상 조회(루트 + 하위 + 버전)는 잠금 밖에서 하고, 잠근 뒤 그 묶음의 휴지통 행 수만 다시 센다.
     * 고정 목록이라 행은 줄기만 하므로(복원·영구 삭제) 수가 같으면 읽은 뒤 바뀐 것이 없고, 줄었으면 잠금 안에서 다시 읽는다.
     * 대상 조회를 잠금 안에 두면 묶음마다 그만큼 같은 사용자의 쓰기가 더 기다린다(부하 테스트 5만 건: 잠금 대기 최대 0.2초 → 1.35초).
     * 목록이 없는 사용자 전체 비우기는 새로 버린 항목이 섞일 수 있어 잠근 뒤 읽는다.
     * <p>
     * 잠금은 {@code FOR NO KEY UPDATE}(복원의 이름 자리 잠금·사용량 UPDATE 와 같은 모드) — 그들과 직렬화되고, 사용자 행을 FK 로 참조하는 INSERT(KEY SHARE)는 막지 않는다.
     */
    private int cleanUpByUser(TrashCleanUpByUserQuery query) {
        List<TrashCleanUpTargetsData> targets;
        if (query.trashSeqs() == null) {
            userRowLock.lockUser(query.userSeq());
            targets = trashCleanUpQueryRepository.findTrashCleanUpTargets(query);
        } else {
            int before = trashCleanUpQueryRepository.countTrashRows(query.userSeq(), query.trashSeqs());
            targets = trashCleanUpQueryRepository.findTrashCleanUpTargets(query);
            userRowLock.lockUser(query.userSeq());
            if (trashCleanUpQueryRepository.countTrashRows(query.userSeq(), query.trashSeqs()) != before) {
                targets = trashCleanUpQueryRepository.findTrashCleanUpTargets(query);
            }
        }

        // 사용자가 직접 휴지통 비우기(WEB) - 삭제되는 리소스마다 영구삭제 감사 로그 fan-out.
        // 스케줄러 자동정리(cleanUpResourcesByDate)는 공유 메서드 cleanUpResources를 쓰되 이 기록은 타지 않는다(SYSTEM 감사 보류).
        recordPermanentDeleteAudit(targets);

        cleanUpResources(targets);
        return targets == null ? 0 : targets.stream().mapToInt(t -> t.getResourceTargets().size()).sum();
    }

    private void recordPermanentDeleteAudit(List<TrashCleanUpTargetsData> targets) {
        if (targets == null) {
            return;
        }
        for (var data : targets) {
            for (var resource : data.getResourceTargets()) {
                EventName eventName = resource.getResourceType().isFile()
                        ? EventName.FILE_PERMANENT_DELETE
                        : EventName.FOLDER_PERMANENT_DELETE;
                resourceAuditService.recordPermanentDelete(eventName, resource.getResourceSeq(), resource.getResourceName());
            }
        }
    }

    private void cleanUpResources(List<TrashCleanUpTargetsData> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        TrashCleanUpEntity entity = toTrashCleanUpEntity(targets);

        Map<Integer, Long> userTotalSizeMap = entity.userTotalSizeMap();
        log.info("[DefaultTrashCleanUpService][cleanUpResources] TrashCleanUpEntity userTotalSizeMap size(): {}", userTotalSizeMap.size());
        for (var entry : userTotalSizeMap.entrySet()) {
            userService.decreaseUseQuotaByte(entry.getValue(), entry.getKey());
        }

        List<PostProcessTrashDto> postProcessTrashDtoList = entity.postProcessTrashDtoList();
        log.info("[DefaultTrashCleanUpService][cleanUpResources] TrashCleanUpEntity postProcessTrashDtoList size(): {}", postProcessTrashDtoList.size());
        for (PostProcessTrashDto postProcessTrashDto : postProcessTrashDtoList) {
            postProcessingOrchestratorRetry.processTrashAndNotify(postProcessTrashDto);
        }

        int processedCount = executeBatch(entity.userResourceGroupList());
        log.info("[DefaultTrashCleanUpService][cleanUpResources] TrashCleanUpEntity executeBatch processedCount: {}", processedCount);

        List<String> deleteKeyList = entity.deleteKeyList();
        log.info("[DefaultTrashCleanUpService][cleanUpResources] TrashCleanUpEntity deleteKeyList size(): {}", deleteKeyList.size());
        publisher.publishEvent(new ResourceDeletedEvent(deleteKeyList));
    }

    private int executeBatch(List<UserResourceGroup> resourceGroups) {
        // tb_resource·tb_resource_version·tb_share·tb_share_target·tb_starred·tb_recent 는 JdbcClient 리포로 이관됨 — 그룹(사용자)별 IN 삭제(각 1건 집계). Trash 도 JPA 리포(JPQL IN 삭제)로 이관되어 MyBatis 배치 사용처 0
        int resourceGroupsDeleted = 0;
        for (UserResourceGroup urg : resourceGroups) {
            resourceRepository.deleteByResourceSeqList(ResourceDeleteSeqListQuery.builder().userSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            resourceVersionRepository.deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery.builder().userSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            shareRepository.deleteByResources(ShareDeleteResourcesQuery.builder().userSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            shareTargetRepository.deleteByResources(ShareTargetDeleteResourcesQuery.builder().sharedByUserSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            starredRepository.deleteByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            recentRepository.deleteByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            resourceGroupsDeleted += 6;
        }

        for (UserResourceGroup urg : resourceGroups) {
            trashRepository.deleteByResources(TrashDeleteResourcesQuery.builder().userSeq(urg.userSeq()).resourceSeqList(urg.resourceSeqList()).build());
            resourceGroupsDeleted += 1;
        }

        return resourceGroupsDeleted;
    }

    private TrashCleanUpEntity toTrashCleanUpEntity(List<TrashCleanUpTargetsData> targets) {
        Map<Integer, Long> userTotalSizeMap = new HashMap<>();
        List<PostProcessTrashDto> postProcessTrashDtoList = new ArrayList<>();
        List<UserResourceGroup> resourceGroupList = new ArrayList<>();
        List<String> deleteKeyList = new ArrayList<>();

        for (var data : targets) {
            Long sizeByte = 0L;
            Integer userSeq = data.getUserSeq();
            String userName = data.getUserName();
            Instant currentTime = Instant.now();
            List<ResourceBrief> resourceBriefList = new ArrayList<>();

            for (var resourceData : data.getResourceTargets()) {
                resourceBriefList.add(new ResourceBrief(resourceData.getResourceSeq(), resourceData.getResourceName()));

                if (!resourceData.getResourceType().isFile()) {
                    continue;
                }

                sizeByte += resourceData.getSizeByte();
                deleteKeyList.add(resourceData.getResourceSaveName());

                for (var resourceVersionData : resourceData.getResourceVersionTargets()) {
                    sizeByte += resourceVersionData.getSizeByte();
                    deleteKeyList.add(resourceVersionData.getResourceSaveName());
                }
            }

            postProcessTrashDtoList.add(new PostProcessTrashDto(
                    userSeq,
                    userName,
                    resourceBriefList,
                    ResourceType.FOLDER,
                    userSeq,
                    currentTime,
                    data.getTenantSeq())
            );
            resourceGroupList.add(data.getUserResourceGroup());
            userTotalSizeMap.put(userSeq, sizeByte);
        }

        return new TrashCleanUpEntity(userTotalSizeMap, postProcessTrashDtoList, resourceGroupList, deleteKeyList);
    }

}
