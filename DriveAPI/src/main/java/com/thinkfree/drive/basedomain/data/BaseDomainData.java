package com.thinkfree.drive.basedomain.data;

import com.thinkfree.drive.basedomain.address.BaseDomainAddress;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 베이스 도메인 — 읽기 전용 값. getter 이름은 엔티티 {@code BaseDomain} 과 같다. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(BaseDomainJpaRepository#findData)
public class BaseDomainData {

    private Integer baseDomainSeq;
    /** 호스트만(소문자, 포트 없음) */
    private String baseDomain;
    /** 아웃바운드 스킴(http|https). null 이면 요청 스킴 → https */
    private String scheme;
    /** 아웃바운드 포트. null 이면 생략 */
    private Integer port;

    /** 호스트만 아는 호출부(테스트 등)용 — 스킴·포트 없음 */
    public BaseDomainData(Integer baseDomainSeq, String baseDomain) {
        this(baseDomainSeq, baseDomain, null, null);
    }

    /** 화면 표시·응답용 {@code [scheme://]host[:port]} */
    public String getAddress() {
        return BaseDomainAddress.display(scheme, baseDomain, port);
    }
}
