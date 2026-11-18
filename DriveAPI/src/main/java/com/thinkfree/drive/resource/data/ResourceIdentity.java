package com.thinkfree.drive.resource.data;

/**
 * 리소스 식별 값(읽기 전용) — 감사 기록의 대상 인자 타입. 감사는 이 값들(seq·이름·부모 폴더·경로)만 읽는다(10-12 단계 4 — 감사 인자를 엔티티에서 분리).
 * {@link ResourceDetailData} 가 구현한다. 엔티티 {@code Resource} 는 구현하지 않는다(W10c — 관리 엔티티를 쥔 쓰기 경로는 {@code ResourceConverter.toDetailData} 로 넘긴다).
 */
public interface ResourceIdentity {

    Long getResourceSeq();

    String getResourceName();

    Long getParentFolderSeq();

    String getFullPath();
}
