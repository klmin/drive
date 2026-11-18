package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.sso.identity.entity.UserSsoIdentity;
import java.time.Instant;
import java.util.List;

/**
 * tb_user_sso_identity 쓰기 — {@code DefaultUserSsoIdentityService}(SSO 가입 save, 로그인 콜백 시각 갱신, 사용자 정리 IN 삭제) 가 주입. 읽기는 {@link UserSsoIdentityQueryRepository}.
 */
public interface UserSsoIdentityRepository {

    /** JPA save(IDENTITY 키라 호출 순간 INSERT) — identity 키·linked_at 이 엔티티에 채워진다 */
    void save(UserSsoIdentity identity);

    /** last_callback_at 단일 컬럼 갱신(로드 없이 1문) */
    void updateLastCallbackAt(Long ssoIdentitySeq, Instant lastCallbackAt);

    /** 빈/널 목록은 SQL 없이 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);
}
