package com.thinkfree.drive.share.enums;

import com.thinkfree.drive.permission.enums.Permission;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShareType {
    SPECIFIC_USERS("특정 사용자만"),
    ORG_LINK("조직 내부 공개"),
    /**
     * 전체 공개(링크를 가진 누구나, 익명 포함). 공개라 ORG_LINK 보다 좁은 권한만 허용.
     */
    PUBLIC_LINK("전체 공개");

    private final String description;

    public boolean isPublic(){
        return this == ORG_LINK || this == PUBLIC_LINK;
    }

    /** 공유 링크 접근 시 전체 공개 여부 판단. ORG_LINK는 포함하지 않는다. */
    public boolean isPublicLink() {
        return this == PUBLIC_LINK;
    }

    /**
     * 리소스 유형(파일/폴더)별 허용 권한. 공유 유형 선택 화면 노출 + 공유 등록/수정 시 권한 검증의 단일 소스.
     * <p>
     * 활성 권한은 VIEW/EDIT/UPLOAD/DOWNLOAD/SHARE 이며, 공유 유형/리소스 유형에 따라 다음 규칙으로 달라진다.
     * <ul>
     *   <li>UPLOAD : 폴더만 (파일에는 업로드 개념 없음), 그리고 PUBLIC_LINK 제외</li>
     *   <li>SHARE : SPECIFIC_USERS 만</li>
     *   <li>PUBLIC_LINK : 파일/폴더 모두 VIEW/EDIT/DOWNLOAD (업로드·공유 없음)</li>
     * </ul>
     */
    public Set<Permission> allowedPermissions(ResourceType resourceType) {
        boolean isFile = resourceType.isFile();
        return switch (this) {
            case SPECIFIC_USERS -> isFile
                    ? EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.DOWNLOAD, Permission.SHARE)
                    : EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.UPLOAD, Permission.DOWNLOAD, Permission.SHARE);
            case ORG_LINK -> isFile
                    ? EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.DOWNLOAD)
                    : EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.UPLOAD, Permission.DOWNLOAD);
            case PUBLIC_LINK -> EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.DOWNLOAD);
        };
    }
}
