package com.thinkfree.drive.resource.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공유 링크 복사(get-or-create) 시 리소스의 현재 토큰과 소유자 테넌트를 조회한 결과.
 * shareToken 이 null 이면 아직 링크가 발급되지 않은 상태.
 * 공개 전체 생성자는 JPQL 생성자 식용(ResourceJpaRepository#findShareTokenForLink, 5차 패스).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceLinkTokenData {
    private String shareToken;
    private Integer tenantSeq;
}
