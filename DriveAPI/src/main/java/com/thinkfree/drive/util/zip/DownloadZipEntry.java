package com.thinkfree.drive.util.zip;

public record DownloadZipEntry
(
        Long resourceSeq,
        Long parentFolderSeq,
        boolean isFolder,
        String resourceName,
        String resourceSaveName,
        String fullPath,
        Integer depth

)
{
}
