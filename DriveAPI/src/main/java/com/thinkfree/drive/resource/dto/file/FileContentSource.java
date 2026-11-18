package com.thinkfree.drive.resource.dto.file;

import com.thinkfree.drive.common.file.model.FileData;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 쓰기(FileWriteOrchestrator)의 새 내용 출처 — 핸들러의 doStorage 가 출처별로 스토리지 호출을 고른다(5차 패스).
 * <ul>
 *   <li>{@link Upload} — 요청 업로드 파일(웹 업로드·MCP 업로드)</li>
 *   <li>{@link Stream} — 이미 열린 스트림(웹오피스 저장·문서 템플릿)</li>
 *   <li>{@link StorageCopy} — 스토리지에 이미 있는 객체(파일 복사·버전 복원)</li>
 * </ul>
 */
public sealed interface FileContentSource {

    record Upload(MultipartFile file) implements FileContentSource {
    }

    record Stream(FileData data) implements FileContentSource {
    }

    record StorageCopy(String sourceKey) implements FileContentSource {
    }
}
