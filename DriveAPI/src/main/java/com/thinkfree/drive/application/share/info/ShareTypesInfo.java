package com.thinkfree.drive.application.share.info;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공유 화면 랜딩 정보 — 웹 ShareTypesResponse와 동형 계약 (필드 의미는 웹 응답 javadoc 참조).
 * 유형별 항목 조립(from)은 구 웹 응답의 정적 팩토리를 그대로 인계.
 */
public record ShareTypesInfo
(
    boolean editable,
    boolean allowSpecificUsers,
    boolean allowOrgLink,
    boolean allowPublic,
    List<ShareTypesItemInfo> shareTypes
)
{
    public record ShareTypesItemInfo
    (
        String code,
        String name,
        Set<String> allowedPermissions
    )
    {
        public static ShareTypesItemInfo from(ShareType type, ResourceType resourceType) {
            return new ShareTypesItemInfo(
                type.name(),
                type.getDescription(),
                type.allowedPermissions(resourceType).stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet())
            );
        }
    }
}
