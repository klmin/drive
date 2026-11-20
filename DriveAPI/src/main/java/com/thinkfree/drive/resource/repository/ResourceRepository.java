package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.resource.data.ResourceCopyNumbers;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.query.*;

import java.util.List;

/**
 * 쓰기 + 쓰기 흐름 안의 읽기(고칠 행 로드·잠금·복사본 번호 집계). Service 가 주입. 읽기 전용 조회는 {@link ResourceQueryRepository}.
 * 단건 변경은 여기서 받은 <b>관리 엔티티</b>를 Service 가 변경 메서드로 고친다(커밋 때 변경 감지 — W10). 그 밖은 조건부 다건·감사 값 지정 JPQL, 동적 SET·잠금 집계 JdbcClient.
 */
public interface ResourceRepository {

    /** 파일·폴더 INSERT — IDENTITY 라 이 호출 안에서 INSERT·id 채움. 반환 인스턴스는 관리 상태(이어서 고친 값은 커밋 때 UPDATE) */
    Resource save(Resource entity);
    /** 고칠 행 로드(관리 엔티티, 잠금 없음). 없으면 null */
    Resource findByUserSeqAndResourceSeq(int userSeq, long resourceSeq);
    /** 고칠 행 로드(관리 엔티티) + 소유 계정 노출 가능 상태(ACTIVE·INACTIVE). 없으면 null */
    Resource findVisibleByUserSeqAndResourceSeq(int userSeq, long resourceSeq);
    /** {@link #findVisibleByUserSeqAndResourceSeq} 의 잠금판(FOR UPDATE) — 그 행의 첫 로드에서. 없으면 null */
    Resource lockVisibleByUserSeqAndResourceSeq(int userSeq, long resourceSeq);
    /** 미반영 변경을 지금 내보냄 — 같은 트랜잭션에서 그 행을 JdbcClient 로 읽을 때만(호출부 주석 필수) */
    void flush();
    /** 같은 폴더의 동명(원본/표시 이름) 행을 FOR UPDATE 로 잠근 뒤 복사본 번호 MAX 집계 */
    ResourceCopyNumbers lockMaxCopyNumbers(ResourceLockQuery query);
    /** 버전 작업용 FOR UPDATE — 미삭제 리소스 행을 잠그고 값(DTO)으로 돌려준다. 없으면 null */
    ResourceDetailData lockResourceForVersioning(long resourceSeq, int userSeq);
    /** 휴지통 보내기·영구 삭제 전 대상 행 FOR UPDATE + 상세(값) — 미삭제·소유 계정 노출 가능. 없으면 null */
    ResourceDetailData lockVisibleActiveDetail(int userSeq, long resourceSeq);
    /** 폴더 하위 트리(자기 자신 포함, 미삭제) 행을 FOR UPDATE 로 잠그고 resource_seq 를 돌려준다 */
    List<Long> lockSubtree(int userSeq, String fullPathSeq);
    /** 행 FOR UPDATE 잠금만 — 없는 행이면 아무것도 안 잠김(존재 확인은 호출부) */
    void lockRow(int userSeq, long resourceSeq);
    int delete(ResourceDeleteQuery query);
    /** 폴더 삭제 — full_path_seq 하위 전체 + 자기 자신(UPDATE … FROM) */
    int updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery query);
    int updateFullPathForChildFolders(ResourcePathUpdateQuery query);
    int restoreChildResources(ResourceRestoreChildQuery query);
    int deleteByResourceSeqList(ResourceDeleteSeqListQuery query);
    int updateUserSeqForResources(ResourcesUserUpdateQuery query);
    /** 덮어쓰기(업로드 세션 커밋) — 저장 key 를 baseKey 일 때만 newKey 로 바꾸고 크기·수정 감사 갱신(낙관적 비교). 쓴 감사 값, 0행(없음·그 사이 내용 바뀜)이면 null */
    UpdateStamp overwrite(int userSeq, long resourceSeq, long sizeByte, String newKey, String baseKey);
    int revertOverwrite(ResourceRevertOverWriteQuery query);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int updateExpiredLocks(ResourceLockExpireQuery query);
    /** 공유 링크 토큰 — 토큰이 없을 때만 대입(동시 발급이면 먼저 쓴 쪽이 이김), 수정 감사 그대로. 반영 행 수(0 = 이미 있음·없는 행) */
    int applyShareTokenIfAbsent(int userSeq, long resourceSeq, String shareToken);
}
