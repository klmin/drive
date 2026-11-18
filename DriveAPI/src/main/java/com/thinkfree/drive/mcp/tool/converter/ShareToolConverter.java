package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.share.info.ShareDetailInfo;
import com.thinkfree.drive.mcp.tool.response.ShareDetailToolResponse;
import org.mapstruct.Mapper;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 ResponseConverter 등가물.
 * 필드는 ShareDetailInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 * Info의 password는 응답 record에 필드가 없어 매핑에서 자연 탈락한다 (ShareDetailToolResponse 주석 참고).
 * 만료일 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateFormat 동일 포맷).
 */
@Mapper(componentModel = "spring")
public interface ShareToolConverter extends ToolDateTimeSupport {

    ShareDetailToolResponse toDetailResponse(ShareDetailInfo info);

}
