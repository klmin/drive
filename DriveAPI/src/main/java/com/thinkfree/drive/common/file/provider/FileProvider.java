package com.thinkfree.drive.common.file.provider;

import com.thinkfree.drive.common.file.domain.FileKey;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.model.FileMetadata;

public interface FileProvider {
    
    FileMetadata getFileMetadata(FileKey key);
    FileData getFileData(FileKey key);
}
