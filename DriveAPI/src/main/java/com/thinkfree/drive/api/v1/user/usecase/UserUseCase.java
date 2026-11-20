package com.thinkfree.drive.api.v1.user.usecase;

import com.thinkfree.drive.api.v1.user.converter.UserResponseConverter;
import com.thinkfree.drive.api.v1.user.request.UserSearchRequest;
import com.thinkfree.drive.api.v1.user.response.UserQuotaResponse;
import com.thinkfree.drive.api.v1.user.response.UserResponse;
import com.thinkfree.drive.api.v1.user.response.UserSearchResponse;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.upload.session.service.UploadSessionTxService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserUseCase {
    
    private final UserResponseConverter userResponseConverter;
    private final UserFindService userFindService;
    private final UserService userService;
    private final TenantPolicy tenantPolicy;
    private final UploadSessionTxService uploadSessionTxService;

    public List<UserSearchResponse> search(UserSearchRequest request, int requestUserSeq){
        var users = userFindService.findOwnerCandidates(request.sk(), RoleType.USER, UserStatus.displayableStatuses(), requestUserSeq);
        return userResponseConverter.toUserSearchResponseList(users);
    }

    /** 내 정보 — 계정 값과 보유 역할 전부(enum 순서)를 한 번에 읽는다. roles 는 토큰의 로그인 역할과 별개 */
    public UserResponse getCurrentUser(Integer userSeq) {
        var user = userService.getCurrentUser(userSeq);
        return UserResponse.of(user, user.getRoles(), tenantPolicy.isSelfPasswordChangeable(userSeq));
    }

    /** 현재 사용량/전체 용량 — 없거나 ACTIVE 아니면 USER_NOT_FOUND(FindService) */
    public UserQuotaResponse getCurrentQuota(Integer userSeq) {
        var quota = userFindService.findUserQuotaByUserSeq(userSeq);
        return UserQuotaResponse.of(quota, uploadSessionTxService.sumUploadingByte(userSeq));
    }

}
