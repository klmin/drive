package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공유 화면 랜딩 응답.
 * <ul>
 *   <li>{@code editable} : 공유 유형을 수정(선택) 가능한지. 상위 계층에 상속된 공유가 있으면 유형이 고정되어 false.</li>
 *   <li>{@code allowSpecificUsers/allowOrgLink/allowPublic} : 테넌트에 실제 적용되는 공유 설정의 접근범위 허용 여부
 *       (슈퍼어드민 enforce / 테넌트 개별 해소 결과 = 어드민이 보는 값과 동일).</li>
 *   <li>{@code shareTypes} : 사용 가능한 공유 유형 + 유형별 허용 권한.</li>
 * </ul>
 *
 * @param editable           공유 유형 수정(선택) 가능 여부
 * @param allowSpecificUsers 접근범위 - 제한됨(직접 추가한 사용자만)
 * @param allowOrgLink       접근범위 - 조직 내부 공개
 * @param allowPublic        접근범위 - 전체 공개
 * @param shareTypes         사용 가능한 공유 유형 목록
 */
public record ShareTypesResponse
(
    boolean editable,
    boolean allowSpecificUsers,
    boolean allowOrgLink,
    boolean allowPublic,
    List<ShareTypesItem> shareTypes
)
{
    public record ShareTypesItem
    (
        String code,
        String name,
        Set<String> allowedPermissions
    )
    {
        public static ShareTypesItem from(ShareType type, ResourceType resourceType) {
            return new ShareTypesItem(
                type.name(),
                type.getDescription(),
                type.allowedPermissions(resourceType).stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet())
            );
        }
    }
}
