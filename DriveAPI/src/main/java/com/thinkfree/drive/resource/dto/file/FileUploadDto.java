package com.thinkfree.drive.resource.dto.file;

import com.thinkfree.drive.resource.enums.ResourceSaveType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Builder(toBuilder = true)
@Getter
public class FileUploadDto {

    private Long resourceSeq;
    private Integer userSeq;
    private Integer ownerSeq;
    private Long parentFolderSeq;
    private String baseFileName;
    private String originalFileName;
    private String originalFileNameWithoutExt;
    private String ext;
    private String contentType;
    private Long fileSize;
    private MultipartFile file;
    /** 새 내용 출처 — 비우면 업로드 파일({@link #file}). 스트림·스토리지 복사는 이 값으로(웹오피스·문서 템플릿·파일 복사·버전 복원) */
    private FileContentSource contentSource;
    private int copyNumber;
    private boolean copyFile;
    private ResourceSaveType resourceSaveType;
    private Integer versionRegisterUserSeq;
    private Integer updateUserSeq;
    private boolean lockedResourceCheck;
    /**
     * 버전 저장(updateWithVersioning)에서 행 잠금 뒤 잠김(is_locked)을 확인한다 — 버전 복원만 켠다(잠긴 파일은 복원 불가, RESOURCE_LOCKED).
     * 업로드·웹오피스 저장은 끈다: 잠금을 쥔 편집기 자신이 이 경로로 저장한다({@link #lockedResourceCheck} 는 업로드가 켜지만 버전 경로에서 읽지 않는다).
     */
    private boolean unlockedRequired;

    /** 핸들러 doStorage 가 쓰는 출처 — {@link #contentSource} 가 없으면 {@link #file} 업로드 */
    public FileContentSource resolveContentSource() {
        return contentSource != null ? contentSource : new FileContentSource.Upload(file);
    }
}
