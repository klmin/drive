package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 매직바이트 검사(docs/design/upload-session-redesign.md 규칙 3, §6.3 ⑤) — 조립·크기 확인이 끝난 객체의 앞 64KB 로 형식을 판별해
 * 확장자가 말하는 형식과 계열이 다르거나(위장) 실행 파일이면 커밋하지 않는다.
 * <ul>
 *   <li>계열은 넓게 나눈다: 미디어(이미지·오디오·비디오), PDF, 컨테이너(zip·OLE·압축·Office), 실행 파일.
 *       같은 계열 안의 차이(docx ↔ xlsx, mp4 ↔ mov, heic ↔ mp4)는 보지 않는다 — 판별이 흔들리고 보안상 의미가 없다</li>
 *   <li>확장자나 내용 어느 한쪽이라도 계열을 모르면(텍스트·HTML·SVG·알 수 없는 바이너리) 통과 — 텍스트류는 내용으로 판별할 수 없다(한계로 수용)</li>
 *   <li>실행 파일(PE·ELF·Mach-O)은 확장자와 상관없이 막는다 — 설치처가 설치 파일을 공유해야 하면
 *       {@code drive.upload.block-executable-content=false}. 그래도 다른 확장자로 위장한 실행 파일은 계열 불일치로 막힌다</li>
 * </ul>
 * 정밀 검사(바이러스)는 범위 밖 — 커밋 뒤 비동기로 따로 한다(설계 §11).
 */
@Slf4j
@Component
public class UploadContentInspector {

    /** 판별에 읽는 앞부분 크기 — 대부분의 매직은 앞 몇 바이트, 컨테이너 판별 여유분 포함 */
    static final int SNIFF_BYTES = 64 * 1024;

    /** detect(byte[])·detect(String) 만 쓴다 — 파사드는 스레드 세이프 */
    private static final Tika TIKA = new Tika();

    enum Family { MEDIA, PDF, CONTAINER, EXECUTABLE, UNKNOWN }

    private final StorageService storageService;
    private final boolean blockExecutables;

    public UploadContentInspector(StorageService storageService,
                                  @Value("${drive.upload.block-executable-content:true}") boolean blockExecutables) {
        this.storageService = storageService;
        this.blockExecutables = blockExecutables;
    }

    /**
     * 스토리지의 객체 앞부분을 읽어 검사한다.
     *
     * @throws ApiRuntimeException 위장·실행 파일 UPLOAD_CONTENT_MISMATCH
     */
    public void verify(String storageKey, String fileName, long size) {
        if (size <= 0) {
            return;
        }
        byte[] head = storageService.readRange(storageKey, 0, Math.min(size, SNIFF_BYTES) - 1);
        verify(head, fileName, storageKey);
    }

    void verify(byte[] head, String fileName, String storageKey) {
        if (head == null || head.length == 0) {
            return;   // 읽을 내용이 없다 — 판별 불가(실제 스토리지는 size > 0 이면 내용이 있다)
        }
        String detected = TIKA.detect(head);                      // 내용만 — 이름을 주면 이름 쪽으로 기운다
        String declared = TIKA.detect(fileName == null ? "" : fileName);   // 확장자 사전 조회
        Family actual = familyOf(detected);
        Family expected = familyOf(declared);

        if (actual == Family.EXECUTABLE && (blockExecutables || expected != Family.EXECUTABLE)) {
            reject(fileName, storageKey, declared, detected);
        }
        if (actual != Family.UNKNOWN && expected != Family.UNKNOWN && actual != expected) {
            reject(fileName, storageKey, declared, detected);
        }
    }

    private static void reject(String fileName, String storageKey, String declared, String detected) {
        log.warn("[UploadContentInspector] content does not match the file name — fileName: {}, declared: {}, detected: {}, key: {}",
                 fileName, declared, detected, storageKey);
        throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_CONTENT_MISMATCH)
                                 .with("declared", declared)
                                 .with("detected", detected);
    }

    static Family familyOf(String mimeType) {
        if (mimeType == null) {
            return Family.UNKNOWN;
        }
        String m = mimeType.toLowerCase(Locale.ROOT);
        int params = m.indexOf(';');
        if (params >= 0) {
            m = m.substring(0, params).trim();
        }
        if (m.equals("application/x-dosexec") || m.equals("application/x-msdownload") || m.equals("application/x-executable")
            || m.equals("application/x-sharedlib") || m.equals("application/x-elf") || m.startsWith("application/x-mach-o")) {
            return Family.EXECUTABLE;
        }
        if (m.equals("application/pdf")) {
            return Family.PDF;
        }
        // SVG 는 XML 텍스트 — 내용으로 판별하지 않는다
        if ((m.startsWith("image/") && !m.contains("svg")) || m.startsWith("audio/") || m.startsWith("video/")) {
            return Family.MEDIA;
        }
        if (m.equals("application/zip") || m.equals("application/x-zip-compressed") || m.equals("application/java-archive")
            || m.equals("application/epub+zip") || m.equals("application/x-tika-ooxml") || m.equals("application/x-tika-msoffice")
            || m.equals("application/x-tika-ooxml-protected") || m.equals("application/msword") || m.equals("application/x-hwp-v5")
            || m.startsWith("application/vnd.openxmlformats-officedocument.") || m.startsWith("application/vnd.oasis.opendocument.")
            || m.equals("application/vnd.ms-excel") || m.equals("application/vnd.ms-powerpoint") || m.startsWith("application/vnd.ms-excel.")
            || m.startsWith("application/vnd.ms-powerpoint.") || m.startsWith("application/vnd.ms-word.")
            || m.equals("application/x-7z-compressed") || m.equals("application/x-rar-compressed") || m.equals("application/vnd.rar")
            || m.equals("application/gzip") || m.equals("application/x-gzip") || m.equals("application/x-tar") || m.equals("application/x-bzip2")
            || m.equals("application/x-xz")) {
            return Family.CONTAINER;
        }
        return Family.UNKNOWN;
    }
}
