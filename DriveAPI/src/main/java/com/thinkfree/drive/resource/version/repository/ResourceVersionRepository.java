package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.query.*;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입) + 쓰기 흐름 안의 읽기(잠금·고칠 행 로드). 목록·단건 등 읽기는 {@link ResourceVersionQueryRepository}.
 * 단건 변경은 {@link #findByUserSeqAndResourceVersionSeq} 관리 엔티티 + {@link ResourceVersion} 변경 메서드(커밋 때 변경 감지 — W8). 잠금·판정 결과는 값({@link ResourceVersionRowData}).
 */
public interface ResourceVersionRepository {

    /** INSERT(identity 키 — 호출 순간 INSERT, 엔티티는 관리 상태로 남아 이어지는 변경이 커밋 때 UPDATE 된다) */
    void save(ResourceVersion entity);
    /** 고칠 행 로드(관리 엔티티) — 파티션 키 user_seq 포함, 없으면 null */
    ResourceVersion findByUserSeqAndResourceVersionSeq(int userSeq, long resourceVersionSeq);
    int delete(ResourceVersionDeleteQuery query);
    int deleteByResource(ResourceVersionDeleteQuery query);
    int deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery query);
    /** 리소스의 모든 버전 행 잠금(version_number 순) — 값만 돌려준다 */
    List<ResourceVersionRowData> lockAllVersionsByResource(int userSeq, long resourceSeq);
    int decreaseAllVersionNumbers(ResourceVersionNumberQuery query);
    int temporaryVersionNumber(ResourceVersionNumberQuery query);
    int increaseAllVersionNumbers(ResourceVersionNumberQuery query);
    int updateUserSeqForResource(ResourceVersionUserUpdateQuery query);
    int updateUserSeqForResources(ResourcesVersionUserUpdateQuery query);
    int deleteByUserSeqList(List<Integer> userSeqList);
}
