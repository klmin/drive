package com.thinkfree.drive.api.v1.admin.response;

import com.thinkfree.drive.api.response.ByteDownloadResponse;

public sealed interface AdminImportCsvResponse
        permits AdminImportCsvResponse.Success, AdminImportCsvResponse.Fail {

    record Success(int insertCount) implements AdminImportCsvResponse {}
    record Fail(ByteDownloadResponse response) implements AdminImportCsvResponse {}
}
