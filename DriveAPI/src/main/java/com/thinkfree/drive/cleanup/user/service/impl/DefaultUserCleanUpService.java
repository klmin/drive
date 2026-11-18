package com.thinkfree.drive.cleanup.user.service.impl;

import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.auth.token.service.TokenService;
import com.thinkfree.drive.cleanup.user.service.UserCleanUpService;
import com.thinkfree.drive.notification.service.NotificationService;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.recent.service.RecentService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityService;
import com.thinkfree.drive.starred.service.StarredService;
import com.thinkfree.drive.trash.service.TrashService;
import com.thinkfree.drive.user.quota.alert.state.service.UserQuotaAlertStateService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.token.service.PasswordResetTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DefaultUserCleanUpService implements UserCleanUpService {
    
    private final NotificationService notificationService;
    private final PinnedFolderService pinnedFolderService;
    private final ResourceService resourceService;
    private final ShareService shareService;
    private final StarredService starredService;
    private final RecentService recentService;
    private final TrashService trashService;
    private final UserQuotaAlertStateService userQuotaAlertStateService;
    private final TokenService tokenService;
    private final UserSsoIdentityService userSsoIdentityService;
    private final ApiKeyService apiKeyService;
    private final UserRoleService userRoleService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final OAuth2ClientConnectionService oAuth2ClientConnectionService;

    @Override
    public void deleteUsersCascade(List<Integer> userList) {
        notificationService.deleteByUsers(userList);
        tokenService.deleteByUsers(userList);
        passwordResetTokenService.deleteByUsers(userList);
        // MCP OAuth 연결·SAS 인가·동의 — 인증은 DELETED 상태로 이미 막히지만 행이 남지 않게
        oAuth2ClientConnectionService.deleteByUsers(userList);
        // API Key 행 삭제 — 인증은 사용자 상태(DELETED) 검사로 이미 401이지만 관리자 키 목록에 ACTIVE로 남던 갭(09-09 실측). INACTIVE 전환은 키 메뉴에서 재활성화될 수 있어 행 삭제로 확정
        apiKeyService.deleteByUsers(userList);
        pinnedFolderService.unpinFoldersByUsers(userList);
        recentService.deleteByUsers(userList);
        starredService.deleteByUsers(userList);
        trashService.deleteByUserSeqList(userList);
        userQuotaAlertStateService.deleteByUserSeqList(userList);
        shareService.deleteByUsers(userList);
        resourceService.deleteByUsers(userList);
        userSsoIdentityService.deleteByUsers(userList);
        // 역할 행 삭제 — tb_user_role 에 FK·CASCADE 가 없다. 삭제 대기는 복원될 수 있어 여기(완전 삭제)에서만 지운다
        userRoleService.deleteByUsers(userList);
    }
}
