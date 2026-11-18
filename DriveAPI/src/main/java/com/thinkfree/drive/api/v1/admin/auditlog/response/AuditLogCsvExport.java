package com.thinkfree.drive.api.v1.admin.auditlog.response;

/**
 * 감사 로그 CSV 내보내기 결과 — 거버넌스가 준 CSV(원본 44열)를 조각으로 받아 그대로 흘려보낸다.
 * 전체를 메모리에 들지 않으므로 응답 크기를 미리 알 수 없다(Content-Length 없음).
 *
 * @param fileName 응답 Content-Disposition에 실을 파일 이름
 * @param body     조각을 받는 즉시 응답 스트림에 쓰는 본문
 */
public record AuditLogCsvExport(String fileName, AuditLogCsvWriter body) {
}
