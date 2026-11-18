package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.sso.provider.entity.SsoProvider;

import java.util.List;

/**
 * 쓰기 전용(Service 가 주입). upsert 는 JdbcClient, IN 삭제는 JPQL. 읽기는 {@link SsoProviderQueryRepository}.
 */
public interface SsoProviderRepository {

    /**
     * INSERT … ON CONFLICT (sso_provider_seq) DO UPDATE — seq 가 없으면 DEFAULT(identity), 충돌 시 display_name·icon_image_key·status·sort_order 갱신 + update_* = 등록 감사 값.
     * 생성(또는 대상) PK 를 반환한다(인자 엔티티는 고치지 않음).
     */
    Integer upsert(SsoProvider entity);

    /** IN 삭제 — 목록이 비면 0 */
    int deleteBySeqList(List<Integer> ssoProviderSeqList);
}
