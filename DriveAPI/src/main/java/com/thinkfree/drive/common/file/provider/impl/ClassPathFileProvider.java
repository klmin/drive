package com.thinkfree.drive.common.file.provider.impl;

import com.thinkfree.drive.common.file.cache.CachedFile;
import com.thinkfree.drive.common.file.domain.FileKey;
import com.thinkfree.drive.common.file.exception.FileErrorCode;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.model.FileMetadata;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class ClassPathFileProvider implements FileProvider {
    
    private final ConcurrentMap<FileKey, CachedFile> cache = new ConcurrentHashMap<>();
    
    @Override
    public FileMetadata getFileMetadata(FileKey key) {
        CachedFile cf = cache.computeIfAbsent(key, this::loadAndBuild);
        return new FileMetadata(cf.contentType(), cf.length());
    }
    
    @Override
    public FileData getFileData(FileKey key) {
        CachedFile cf = cache.computeIfAbsent(key, this::loadAndBuild);
        return new FileData(cf.contentType(), new ByteArrayInputStream(cf.bytes()), cf.length());
    }
    
    private CachedFile loadAndBuild(FileKey key) {
        var path = key.path();
        byte[] bytes = loadBytes(path);
        
        if(DriveConstants.NOTE_EXTENSION.equals(ResourceUtil.getFileExtension(path))){
            return new CachedFile(bytes, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes.length);
        }
        
        String contentType = resolveContentType(key.path());
        return new CachedFile(bytes, contentType, bytes.length);
    }
    
    private byte[] loadBytes(String classpath) {
        var resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            log.error("[loadBytes] classpath resource not found: {}", classpath);
            throw ApiRuntimeException.type(FileErrorCode.CLASSPATH_NOT_FOUND);
        }
        try (InputStream in = resource.getInputStream()) {
            return FileCopyUtils.copyToByteArray(in);
        } catch (IOException e) {
            log.error("[loadBytes] failed to read: {}", classpath, e);
            throw ApiRuntimeException.type(FileErrorCode.CLASSPATH_READ_FAIL);
        }
    }
    
    private String resolveContentType(String path) {
        return MediaTypeFactory.getMediaType(path)
                               .map(MediaType::toString)
                               .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }
    
}
