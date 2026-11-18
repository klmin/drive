package com.thinkfree.drive.util.file;

public record FileNameParts
(
    String baseFileName,
    int copyNumber,
    boolean copyFile
)
{
}
