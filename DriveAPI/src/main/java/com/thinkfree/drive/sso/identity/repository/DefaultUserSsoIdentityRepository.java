package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.sso.identity.entity.UserSsoIdentity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_user_sso_identity 쓰기 — 구 UserSsoIdentityMapper.xml 의 save·updateLastCallbackAt·deleteByUserSeqList 를 전부 JPA 로(§1 v2 "JPA 우선", JdbcClient 0). 읽기는 {@link DefaultUserSsoIdentityQueryRepository}.
 * <ul>
 *   <li>{@code save} = {@code save}(W4 — IDENTITY 키라 호출 순간 INSERT, 유니크 위반도 그 자리에서 {@code DataIntegrityViolationException}) — identity 키가 엔티티에 채워지고(구 XML {@code useGeneratedKeys}), linked_at 은 Spring Data Auditing(현재 시각), last_callback_at 은 null</li>
 *   <li>{@code updateLastCallbackAt} = JPQL {@code @Modifying} — 로그인마다 로드 없이 끝내는 단일 컬럼 갱신이라 변경 감지 대상 아님(유지 예외)</li>
 *   <li>{@code deleteByUserSeqList} = JPQL {@code @Modifying} — 조건부 다건 삭제(유지 예외)</li>
 *   <li>IN 삭제는 목록이 비면 SQL 없이 0(구 foreach 는 {@code IN ()} 문법 오류)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserSsoIdentityRepository implements UserSsoIdentityRepository {

    private final UserSsoIdentityJpaRepository jpaRepository;

    @Override
    public void save(UserSsoIdentity entity) {
        jpaRepository.save(entity);
    }

    @Override
    public void updateLastCallbackAt(Long ssoIdentitySeq, Instant lastCallbackAt) {
        jpaRepository.updateLastCallbackAt(ssoIdentitySeq, lastCallbackAt);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}
