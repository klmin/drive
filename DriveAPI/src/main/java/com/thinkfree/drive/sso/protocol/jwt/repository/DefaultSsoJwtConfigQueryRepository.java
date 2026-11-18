package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_sso_jwt_config 읽기 — 전부 JPQL 생성자 식(엔티티 로드·detach 없음, W4)({@link SsoJwtConfigJpaRepository}). 쓰기는 {@link DefaultSsoJwtConfigRepository}.
 * <p>
 * 결과는 설정 8컬럼({@link SsoJwtConfigData}, 감사 없음) 한 형태. findByIssuerAndScope 만 SsoProvider 와 조인해 scope 로 거른다. IN 목록이 비면 쿼리 없이 빈 목록.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoJwtConfigQueryRepository implements SsoJwtConfigQueryRepository {

    private final SsoJwtConfigJpaRepository jpaRepository;

    @Override
    public SsoJwtConfigData findById(SsoProtocolFindQuery q) {
        Integer seq = q.getSsoProviderSeq();
        return seq == null ? null : jpaRepository.findDataById(seq).orElse(null);
    }

    @Override
    public SsoJwtConfigData findByIssuer(String issuer) {
        return jpaRepository.findDataByIssuer(issuer).orElse(null);
    }

    @Override
    public SsoJwtConfigData findByIssuerAndScope(String issuer, SsoProviderScope scope) {
        return jpaRepository.findDataByIssuerAndScope(issuer, scope).orElse(null);
    }

    @Override
    public List<SsoJwtConfigData> findAllByProviderSeqList(SsoProtocolBySeqListQuery q) {
        List<Integer> seqList = q.getSsoProviderSeqList();
        if (seqList == null || seqList.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllDataBySsoProviderSeqIn(seqList);
    }
}
