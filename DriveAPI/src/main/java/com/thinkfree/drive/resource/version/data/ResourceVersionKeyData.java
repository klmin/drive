package com.thinkfree.drive.resource.version.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 버전 한 건의 식별·크기·스토리지 키 — 읽기 전용 값(버전 삭제·영구 삭제·버전 통계). getter 이름은 엔티티 {@code ResourceVersion} 과 같다.
 * 기존 {@link ResourceVersionData}(목록 화면용)는 resourceSaveName 이 없어 따로 둔다.
 */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(ResourceVersionJpaRepository#SELECT_KEY_DATA) — 필드 순서를 바꾸면 JPQL 도 맞출 것
public class ResourceVersionKeyData {

    private Long resourceVersionSeq;
    private Long resourceSeq;
    private Integer userSeq;
    private Integer versionNumber;
    private Long sizeByte;
    private String resourceSaveName;
}
