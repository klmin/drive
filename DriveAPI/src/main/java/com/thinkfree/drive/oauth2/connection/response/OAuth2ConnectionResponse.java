package com.thinkfree.drive.oauth2.connection.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;

import java.time.Instant;
import java.util.List;

/**
 * 연동 앱 목록 항목 — 설정 화면의 "연결된 앱" 행.
 * clientName은 클라이언트가 DCR로 스스로 등록한 표시명이며, 등록 행이 사라진 연결은
 * clientId로 대체 표시된다. lastUsedDate는 5분 스로틀 기록이라 최대 5분 과거일 수 있고,
 * 연결 후 한 번도 사용하지 않았으면 null.
 * Instant 필드지만 @DriveDateTimeFormat의 timezone(Asia/Seoul)이 직렬화 시 KST 벽시계로
 * 렌더링해 기존 웹 응답 표기와 동일하게 나간다.
 */
public record OAuth2ConnectionResponse
(
    Long connectionSeq,
    String clientId,
    String clientName,
    List<String> scopes,
    @DriveDateTimeFormat
    Instant registerDate,
    @DriveDateTimeFormat
    Instant lastUsedDate
)
{
}
