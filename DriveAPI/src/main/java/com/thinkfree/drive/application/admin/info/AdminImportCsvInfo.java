package com.thinkfree.drive.application.admin.info;

import com.thinkfree.drive.api.response.ByteDownloadResponse;

/**
 * CSV 사용자 일괄 등록 결과 — 웹 응답(AdminImportCsvResponse)과 동형의 성공/실패 합타입.
 * ByteDownloadResponse는 api.v1 어댑터 객체가 아닌 공용 다운로드 봉투(도메인 서비스도 사용)라 그대로 쓴다.
 */
public sealed interface AdminImportCsvInfo
        permits AdminImportCsvInfo.Success, AdminImportCsvInfo.Fail {

    record Success(int insertCount) implements AdminImportCsvInfo {}
    record Fail(ByteDownloadResponse response) implements AdminImportCsvInfo {}
}
