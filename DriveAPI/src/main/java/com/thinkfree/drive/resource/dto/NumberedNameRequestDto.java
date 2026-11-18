package com.thinkfree.drive.resource.dto;

public record NumberedNameRequestDto
(
        boolean copyFile,
        String originalFileName,
        String baseFileName,
        int copyNumber,
        String ext,
        String originalFileNameWithOutExt
)
{
}
