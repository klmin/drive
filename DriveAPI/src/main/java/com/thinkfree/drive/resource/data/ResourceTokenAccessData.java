package com.thinkfree.drive.resource.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공유 링크 토큰으로 리소스에 접근할 때 토큰을 리소스로 해소한 결과.
 * 권한(공유 존재/유형)은 이 결과의 resourceSeq/userSeq 로 ResourcePermission 에서 재판정한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceTokenAccessData {
    private Long resourceSeq;
    private Integer userSeq;
    private Integer tenantSeq;
    private ResourceType resourceType;
}
