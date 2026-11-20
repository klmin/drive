package com.thinkfree.drive.resource.version.data;

import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 버전 한 행의 값 — 쓰기 흐름의 잠금·만료 판정 결과이자, 다른 트랜잭션(업로드 후 finalize·저장 실패 롤백)과 리소스 롤백 쿼리 변환으로 넘기는 운반 값(W8).
 * 엔티티를 넘기면 detach 가 필요하고, 받은 쪽이 고치면(구 increaseVersionNumber) 커밋 때 UPDATE 가 나갈 수 있어 값으로 바꿨다.
 * getter 이름은 엔티티 {@code ResourceVersion} 과 같다(MapStruct 이름 매핑·로그 줄 무변경).
 */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(ResourceVersionJpaRepository#SELECT_ROW_DATA) — 필드 순서를 바꾸면 JPQL 도 맞출 것
public class ResourceVersionRowData {

    private Long resourceVersionSeq;
    private Integer userSeq;
    private Integer ownerSeq;
    private Long resourceSeq;
    private String resourceSaveName;
    private Integer versionNumber;
    private Long sizeByte;
    private ResourceVersionStatus status;
    private Instant versionRegisterDate;
    private Integer updateUserSeq;
    private Instant updateDate;
}
