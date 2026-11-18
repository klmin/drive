package com.thinkfree.drive.resource.result;

import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;

import java.time.Instant;

public record ResourceSaveResult
(
    Long resourceSeq,
	Integer userSeq,
    Long resourceVersionSeq,
	Long latestOwnerSeq,
	String parentFolderName,
    String resourceName,
    String resourceSaveName,
    String versionResourceSaveName,
    Integer currentVersionNumber,
    Instant versionRegisterDate,
	Long sizeByte,
	Long rollbackSizeByte,
	Instant occurredDatetime,
	Integer updateUserSeq,
	Instant updateDate,
	ResourceVersionRowData rollbackData,
    Long restoreSizeByte,
    Integer restoreUpdateUserSeq,
    Instant restoreUpdateDate
)
{
}
