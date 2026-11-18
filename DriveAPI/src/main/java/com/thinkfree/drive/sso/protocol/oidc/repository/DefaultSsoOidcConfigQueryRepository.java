package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_sso_oidc_config 읽기 — 전부 JPQL 생성자 식(엔티티 로드·detach 없음, W4)({@link SsoOidcConfigJpaRepository}). 쓰기는 {@link DefaultSsoOidcConfigRepository}.
 * <p>
 * 결과는 설정 6컬럼({@link SsoOidcConfigData}, 감사 없음) 한 형태. {@code allow_idp_initiated} Y/N 은 엔티티의 컨버터가 Boolean 으로. IN 목록이 비면 쿼리 없이 빈 목록.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoOidcConfigQueryRepository implements SsoOidcConfigQueryRepository {

    private final SsoOidcConfigJpaRepository jpaRepository;

    @Override
    public SsoOidcConfigData findById(SsoProtocolFindQuery q) {
        Integer seq = q.getSsoProviderSeq();
        return seq == null ? null : jpaRepository.findDataById(seq).orElse(null);
    }

    @Override
    public List<SsoOidcConfigData> findAllByProviderSeqList(SsoProtocolBySeqListQuery q) {
        List<Integer> seqList = q.getSsoProviderSeqList();
        if (seqList == null || seqList.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllDataBySsoProviderSeqIn(seqList);
    }
}
