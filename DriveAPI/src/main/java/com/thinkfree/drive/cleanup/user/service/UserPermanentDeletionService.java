package com.thinkfree.drive.cleanup.user.service;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.cleanup.user.converter.UserCleanUpConverter;
import com.thinkfree.drive.cleanup.user.service.dto.UserCleanUpDto;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.user.admin.exception.exception.AdminUserErrorCode;
import com.thinkfree.drive.user.admin.service.AdminUserFindService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserPermanentDeletionService {
    
    private final UserCleanUpConverter userCleanUpConverter;
    private final AdminUserFindService adminUserFindService;
    private final AdminUserService adminUserService;
    private final ResourceFindService resourceFindService;
    private final UserCleanUpService userCleanUpService;
    private final UserFindService userFindService;
    private final ApplicationEventPublisher publisher;
    private final UserAuditService userAuditService;
    private final UserRoleFindService userRoleFindService;
    
    public void deletePermanentlyFromPending(UserCleanUpDto dto){

        var userPendingDeleteTargetQuery = userCleanUpConverter.toUserPendingDeleteTargetQuery(dto);
        var pendingDeleteTargets = adminUserFindService.findPendingDeleteTargets(userPendingDeleteTargetQuery);
        validatePendingDeleteTargetCount(dto.userSeqList(), pendingDeleteTargets);

        if(pendingDeleteTargets.isEmpty()){
            return;
        }

        List<Integer> pendingDeleteUserSeqList = new ArrayList<>();
        List<String> profileImageKeys = new ArrayList<>();

        for(UserPendingDeleteData pendingDeleteTarget : pendingDeleteTargets){
            pendingDeleteUserSeqList.add(pendingDeleteTarget.getUserSeq());
            var profileImageKey = pendingDeleteTarget.getProfileImageKey();
            if(StringUtils.hasText(profileImageKey)){
                profileImageKeys.add(profileImageKey);
            }
        }

        deleteUsersAndMarkAsDeleted(pendingDeleteUserSeqList, profileImageKeys, dto.actorUserSeq(), true);

        // 행위자: WEB(관리자 수동)이든 스케줄러(자동삭제)든 dto.actorUserSeq()로 넘어온 값(WEB=관리자, 스케줄러=슈퍼어드민).
        // 스케줄러 경로는 요청/인증 컨텍스트가 없으므로 actor/tenantSeq를 명시로 override해야 한다.
        UserDetailData actor = userFindService.findDetailByUserSeq(dto.actorUserSeq());
        for (UserPendingDeleteData pendingDeleteTarget : pendingDeleteTargets) {
            EventName eventName = RoleType.ADMIN == pendingDeleteTarget.getRoleType() ? EventName.ADMIN_PERMANENT_DELETE : EventName.USER_PERMANENT_DELETE;
            userAuditService.record(eventName, pendingDeleteTarget, actor, pendingDeleteTarget.getTenantSeq());
        }
    }

    public void deleteUserDirectly(int userSeq, int actorUserSeq){

        var user = userFindService.findDetailByUserSeq(userSeq);
        // 감사 이벤트용 역할은 삭제 전에 — cascade 가 tb_user_role 행을 지운다
        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_PERMANENT_DELETE : EventName.USER_PERMANENT_DELETE;

        List<String> profileImageKeys = new ArrayList<>();
        var profileImageKey = user.getProfileImageKey();
        if(StringUtils.hasText(profileImageKey)){
            profileImageKeys.add(profileImageKey);
        }

        deleteUsersAndMarkAsDeleted(List.of(userSeq), profileImageKeys, actorUserSeq, false);

        userAuditService.record(eventName, user);
    }

    /**
     * 테넌트 삭제용 — 테넌트의 DELETED 가 아닌 계정 전부를 영구 삭제(파일·프로필 스토리지 키 수집 → 연관 행 삭제 → DELETED 표시 → 커밋 후 스토리지 삭제).
     * 보호 사용자 검사·감사 기록은 하지 않는다(테넌트 삭제는 감사 로그 없음). 삭제한 계정 수.
     */
    public int deleteAllInTenant(int tenantSeq, int actorUserSeq){

        var users = userFindService.findUndeletedDetailsByTenantSeq(tenantSeq);
        if(users.isEmpty()){
            return 0;
        }

        List<Integer> userSeqList = new ArrayList<>();
        List<String> profileImageKeys = new ArrayList<>();
        for(var user : users){
            userSeqList.add(user.getUserSeq());
            if(StringUtils.hasText(user.getProfileImageKey())){
                profileImageKeys.add(user.getProfileImageKey());
            }
        }

        deleteUsersAndMarkAsDeleted(userSeqList, profileImageKeys, actorUserSeq, false);
        return userSeqList.size();
    }

    private void deleteUsersAndMarkAsDeleted(List<Integer> userSeqList, List<String> profileImageKeys, int actorUserSeq, boolean requirePendingDelete){

        List<String> storageKeys = new ArrayList<>(profileImageKeys);

        var resourceStorageKeys = resourceFindService.findStorageKeysByUserSeqList(userSeqList);
        for(var resourceStorageKey : resourceStorageKeys){
            storageKeys.add(resourceStorageKey.getResourceSaveName());
        }

        userCleanUpService.deleteUsersCascade(userSeqList);

        var userDeleteMarkQuery = userCleanUpConverter.toUserDeleteMarkQuery(userSeqList, actorUserSeq, requirePendingDelete);
        adminUserService.markUsersAsDeleted(userDeleteMarkQuery);

        publisher.publishEvent(new ResourceDeletedEvent(storageKeys));
    }

    private void validatePendingDeleteTargetCount(List<Integer> requestedUserSeqList, List<UserPendingDeleteData> pendingDeleteTargets) {
        if(requestedUserSeqList == null){
            return;
        }
        if (requestedUserSeqList.size() != pendingDeleteTargets.size()) {
            throw ApiRuntimeException.type(AdminUserErrorCode.PERMANENT_DELETE_TARGET_MISMATCH);
        }
    }
}
