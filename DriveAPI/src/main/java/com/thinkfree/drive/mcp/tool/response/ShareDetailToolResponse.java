package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 공유 상세 API 명세와 동형 계약 — 필드는 웹 응답(ShareDetailResponse)과 동일하되,
 * password만 의도적으로 제외한다(공유 비밀번호 평문을 LLM 컨텍스트에 노출하지 않음 — 명세 동형의 유일한 예외,
 * 설정 여부는 isPasswordEnabled로 충분). 날짜는 String — ToolDateTimeSupport 참고.
 */
public record ShareDetailToolResponse
(
    Long shareSeq,
    Integer userSeq,
    Long resourceSeq,
    String shareType,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canDelete,
    Boolean canShare,
    Boolean isPasswordEnabled,
    String expireDate,
    Boolean modifiable,
    List<ShareTargetItem> targets
)
{
    public record ShareTargetItem
    (
        Long shareTargetSeq,
        Integer userSeq,
        String userId,
        String userName,
        UserStatus userStatus,
        Integer tenantSeq,
        String tenantName,
        Boolean canView,
        Boolean canEdit,
        Boolean canDownload,
        Boolean canUpload,
        Boolean canDelete,
        Boolean canShare
    )
    {
    }
}
