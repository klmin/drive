package com.thinkfree.drive.resource.query;


import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceExistsQuery {
    
    private Integer userSeq;
    private Long parentFolderSeq;
    private String originalFileName;
    private String baseFileName;
    private ResourceType resourceType;
    private Boolean isDelete;
    /** 결과에서 뺄 리소스(이름 변경 대상 자신). null 이면 제외 없음 */
    private Long excludeResourceSeq;

}
