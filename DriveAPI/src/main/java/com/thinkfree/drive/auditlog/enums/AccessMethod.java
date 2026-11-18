package com.thinkfree.drive.auditlog.enums;

// 이벤트가 발생한 요청 경로
/**
 * WEB(일반 사용자 요청)
 * API(API Key 인증 요청 - /api/public/** 및 API Key 로 호출한 내부 경로)
 * MCP(MCP 에이전트 요청, /mcp/**)
 * SYSTEM(HTTP 요청 없이 내부 로직 판단으로 발생 - 스케줄러 등)
 */
public enum AccessMethod {
    WEB,
    API,
    MCP,
    SYSTEM
}
