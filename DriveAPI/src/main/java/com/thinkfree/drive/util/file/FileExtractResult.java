package com.thinkfree.drive.util.file;

public record FileExtractResult
(
    String baseFileName,
    String originalFileName,
    String originalFileNameWithoutExt,
    String ext,
    String contentType,
    Long fileSize,
    int copyNumber,
    boolean copyFile
)
{
}
