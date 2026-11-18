package com.thinkfree.drive.util;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.util.exception.ResourceUtilErrorCode;
import com.thinkfree.drive.util.file.FileExtractResult;
import com.thinkfree.drive.util.file.FileNameParts;
import com.thinkfree.drive.util.zip.DownloadZipEntry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ResourceUtil {
    
    private static final Pattern LAST_NUMBER_PATTERN = Pattern.compile("^(.*) \\((\\d+)\\)(\\.[^.]*)?$");

    public static FileExtractResult extract(MultipartFile file) {

        String originalFilename = file.getOriginalFilename();

        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("파일명이 비어 있거나 유효하지 않습니다.");
        }

        FileNameParts fileNameParts = extractFileNameParts(originalFilename);

        return new FileExtractResult(
                fileNameParts.baseFileName(),
                originalFilename,
                removeFileExtension(originalFilename),
                getFileExtension(originalFilename),
                file.getContentType(),
                file.getSize(),
                fileNameParts.copyNumber(),
                fileNameParts.copyFile()
        );

    }
    
    public static FileExtractResult extract(String resourceName){
        FileNameParts fileNameParts = extractFileNameParts(resourceName);
        return new FileExtractResult(
                fileNameParts.baseFileName(),
                resourceName,
                removeFileExtension(resourceName),
                getFileExtension(resourceName),
                null,
                null,
                fileNameParts.copyNumber(),
                fileNameParts.copyFile()
        );
    }
    
    public static boolean isCopiedResourceByName(String resourceName) {
        Matcher matcher = LAST_NUMBER_PATTERN.matcher(resourceName);
        return matcher.matches();
    }
    
    public static FileNameParts extractFileNameParts(String filename) {
        
        Matcher matcher = LAST_NUMBER_PATTERN.matcher(filename);
        
        if (matcher.matches()) {
            String nameWithoutNumber = matcher.group(1);
            String numberStr = matcher.group(2);
            int copyNumber = Integer.parseInt(numberStr);
            String extension = matcher.group(3) != null ? matcher.group(3) : "";
            String baseFileName = nameWithoutNumber + extension;
            
            return new FileNameParts(baseFileName, copyNumber, true);
        }
        
        return new FileNameParts(filename, 0, false);
        
    }
    
    public static StreamingResponseBody createFileStream(FileData fileData){
        return outputStream -> {
                try (InputStream fileStream = fileData.content()) {
                    fileStream.transferTo(outputStream);
                } catch (IOException e) {
                    log.error("[createFileStream Exception]", e);
                    throw ApiRuntimeException.type(ResourceUtilErrorCode.FILE_STREAM_TRANSFER_FAILED);
                }
            };
    }
    
    public static StreamingResponseBody createZipStream(List<DownloadZipEntry> entries, Function<String, FileData> fileDataProvider){
        return outputStream -> {
                try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
        
                    for (DownloadZipEntry entry : entries) {
                        String path = entry.fullPath();
        
                        if (entry.isFolder()) {
                            zos.putNextEntry(new ZipEntry(path));
                            zos.closeEntry();
                            continue;
                        }
        
                        FileData fileData = fileDataProvider.apply(entry.resourceSaveName());
        
                        try (InputStream stream = fileData.content()) {
                            zos.putNextEntry(new ZipEntry(path));
                            stream.transferTo(zos);
                            zos.closeEntry();
                        }
                    }
        
                    zos.finish();
        
                } catch (StorageException | IOException e) {
                    log.error("[createZipStream Exception] ", e);
                    throw ApiRuntimeException.type(ResourceUtilErrorCode.ZIP_CREATE_FAILED);
                }
            };
        
    }
    
    public static ContentDisposition createContentDisposition(String fileName) {
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
    }
    
    public static String removeFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex != -1 ? filename.substring(0, dotIndex) : filename;
    }

    /** 이름 끝의 확장자(".ext", 대소문자 무시)를 한 번만 떼어낸 이름 — 그 확장자로 끝나지 않으면 그대로 */
    public static String stripExtension(String name, String ext) {
        String suffix = "." + ext;
        int baseLength = name.length() - suffix.length();
        return baseLength >= 0 && name.regionMatches(true, baseLength, suffix, 0, suffix.length())
                ? name.substring(0, baseLength)
                : name;
    }

    /** 확장자를 뺀 이름 — 확장자는 이름이 아니라 ext 기준(ext 없음 = 이름 전체, null = 마지막 점 기준) */
    public static String nameWithoutExtension(String name, String ext) {
        if (ext == null) {
            return removeFileExtension(name);
        }
        return ext.isEmpty() ? name : stripExtension(name, ext);
    }

    /** 확장자를 아는 이름 분해 — 확장자 없는 파일의 "a.txt" 는 이름 전체 */
    public static FileExtractResult extract(String resourceName, String ext) {
        FileNameParts fileNameParts = extractFileNameParts(resourceName);
        return new FileExtractResult(
                fileNameParts.baseFileName(),
                resourceName,
                nameWithoutExtension(resourceName, ext),
                ext == null ? getFileExtension(resourceName) : ext,
                null,
                null,
                fileNameParts.copyNumber(),
                fileNameParts.copyFile()
        );
    }

    public static String getFileExtension(String filename) {
        return filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1) : "";
    }
    
    public static String generateKey(Integer userSeq, Long fileSeq){
        return userSeq.toString() + "/" + fileSeq.toString();
    }

    public static String generateExtension(String ext) {
        return StringUtils.hasText(ext) ? "." + ext : "";
    }
    
    public static boolean isOwner(int userSeq, int requestUserSeq) {
        return userSeq == requestUserSeq;
    }
}
