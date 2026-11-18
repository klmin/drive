package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * tb_resource_version 쓰기 + 쓰기 흐름 안의 읽기(FOR UPDATE 잠금·처리 중 여부·만료 단건·고칠 행 로드) — 전부 JPA({@link ResourceVersionJpaRepository}, JdbcClient 0). 읽기는 {@link DefaultResourceVersionQueryRepository}.
 * <ul>
 *   <li>{@code save} = {@link ResourceVersionJpaRepository#save}(IDENTITY 라 호출 순간 INSERT·PK 주입, 등록 감사는 Auditing) — 엔티티는 관리 상태로 남아 이어지는 저장 이름 변경이 커밋 때 변경 감지 UPDATE(W8)</li>
 *   <li>단건 변경(저장 이름·상태) = Service 가 {@code findByUserSeqAndResourceVersionSeq} 관리 엔티티를 변경 메서드로 고침(감사는 Auditing — flush·detach·write-back 없음)</li>
 *   <li>남은 JPQL {@code @Modifying} = 버전 번호 일괄 이동·소유자 이전(조건부 다건) — 수정 감사는 {@link UpdateStamp}({@link JdbcAudit} 규칙).
 *       번호 재배열(temporary → increase/decrease)은 UNIQUE(user_seq, resource_seq, version_number, status) 를 피하기 위한 2단계 UPDATE — 쿼리 객체의 tempOffset 으로 delta 를 만든다</li>
 *   <li>잠금 = {@code @Lock(PESSIMISTIC_WRITE)} 생성자 식, 처리 중·만료 판정 = 애플리케이션 시각 경계(구 {@code NOW() ± make_interval}). 잠금·만료 결과는 값({@link ResourceVersionRowData}) — detach 없음</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceVersionRepository implements ResourceVersionRepository {

    private final ResourceVersionJpaRepository jpaRepository;

    // ------------------------------------------------------------------ INSERT

    @Override
    public void save(ResourceVersion e) {
        jpaRepository.save(e);
    }

    // ------------------------------------------------------------------ 쓰기 흐름 안의 읽기

    @Override
    public ResourceVersion findByUserSeqAndResourceVersionSeq(int userSeq, long resourceVersionSeq) {
        return jpaRepository.findByUserSeqAndResourceVersionSeq(userSeq, resourceVersionSeq).orElse(null);
    }

    @Override
    public List<ResourceVersionRowData> lockAllVersionsByResource(int userSeq, long resourceSeq) {
        return jpaRepository.lockAllByResource(userSeq, resourceSeq);
    }

    @Override
    public boolean existsAliveVersion(ResourceVersionFindQuery q) {
        return jpaRepository.existsAlive(q.getResourceSeq(), q.getUserSeq(), q.getStatus(), threshold(q));
    }

    @Override
    public ResourceVersionRowData findExpiredVersion(ResourceVersionFindQuery q) {
        return jpaRepository.findExpired(q.getResourceSeq(), q.getUserSeq(), q.getStatus(), threshold(q), Limit.of(1)).stream()
                .findFirst()
                .orElse(null);
    }

    /** 현재 시각 - minute 분 */
    private static Instant threshold(ResourceVersionFindQuery q) {
        return Instant.now().minus(Duration.ofMinutes(q.getMinute()));
    }

    // ------------------------------------------------------------------ UPDATE(JPQL — 조건부 다건, 유지 예외)

    /** version_number + tempOffset */
    @Override
    public int temporaryVersionNumber(ResourceVersionNumberQuery q) {
        return shiftVersionNumbers(q, q.getTempOffset());
    }

    /** (version_number - tempOffset) + 1 */
    @Override
    public int increaseAllVersionNumbers(ResourceVersionNumberQuery q) {
        return shiftVersionNumbers(q, -q.getTempOffset() + 1);
    }

    /** (version_number - tempOffset) - 1 */
    @Override
    public int decreaseAllVersionNumbers(ResourceVersionNumberQuery q) {
        return shiftVersionNumbers(q, -q.getTempOffset() - 1);
    }

    private int shiftVersionNumbers(ResourceVersionNumberQuery q, int delta) {
        return jpaRepository.shiftVersionNumbers(q.getUserSeq(), q.getResourceSeq(), q.getStatus(), q.getVersionNumber(), delta,
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    @Override
    public int updateUserSeqForResource(ResourceVersionUserUpdateQuery q) {
        if (q.getResourceSeq() == null) {
            return 0;
        }
        return jpaRepository.updateUserSeqForResources(q.getUserSeq(), List.of(q.getResourceSeq()), q.getChangeUserSeq(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    @Override
    public int updateUserSeqForResources(ResourcesVersionUserUpdateQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.updateUserSeqForResources(q.getUserSeq(), q.getResourceSeqList(), q.getChangeUserSeq(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    // ------------------------------------------------------------------ DELETE

    @Override
    public int delete(ResourceVersionDeleteQuery q) {
        return jpaRepository.deleteVersion(q.getResourceVersionSeq(), q.getUserSeq());
    }

    @Override
    public int deleteByResource(ResourceVersionDeleteQuery q) {
        if (q.getResourceSeq() == null) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), List.of(q.getResourceSeq()));
    }

    @Override
    public int deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), q.getResourceSeqList());
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}
