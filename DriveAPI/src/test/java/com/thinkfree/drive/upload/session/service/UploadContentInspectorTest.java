package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("UploadContentInspector — 매직바이트로 확장자 위장·실행 파일 차단")
class UploadContentInspectorTest {

    private final StorageService storage = Mockito.mock(StorageService.class);
    private final UploadContentInspector blocking = new UploadContentInspector(storage, true);
    private final UploadContentInspector allowingExe = new UploadContentInspector(storage, false);

    // ------------------------------------------------------------------ 샘플(앞부분만)

    private static byte[] png() {
        return pad(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'});
    }

    private static byte[] jpeg() {
        return pad(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0});
    }

    private static byte[] pdf() {
        return "%PDF-1.7\n%âã\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] docx() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] ole2() {
        return pad(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
    }

    /** Windows PE — "MZ" 헤더, 0x3C 에 PE 헤더 위치, 거기에 "PE\0\0" */
    private static byte[] pe() {
        byte[] b = new byte[512];
        b[0] = 'M';
        b[1] = 'Z';
        b[0x3C] = (byte) 0x80;
        b[0x80] = 'P';
        b[0x81] = 'E';
        return b;
    }

    private static byte[] elf() {
        return pad(new byte[]{0x7F, 'E', 'L', 'F', 2, 1, 1, 0});
    }

    private static byte[] mp4() {
        return pad(new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 'i', 's', 'o', 'm', 'i', 's', 'o', '2'});
    }

    private static byte[] text() {
        return "hello, plain text\nsecond line\n".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pad(byte[] head) {
        byte[] b = new byte[Math.max(256, head.length)];
        System.arraycopy(head, 0, b, 0, head.length);
        return b;
    }

    private void passes(UploadContentInspector inspector, byte[] content, String fileName) {
        assertThatCode(() -> inspector.verify(content, fileName, "k")).as(fileName).doesNotThrowAnyException();
    }

    private void rejects(UploadContentInspector inspector, byte[] content, String fileName) {
        assertThatThrownBy(() -> inspector.verify(content, fileName, "k")).as(fileName)
                .isInstanceOfSatisfying(ApiRuntimeException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_CONTENT_MISMATCH));
    }

    // ------------------------------------------------------------------ 맞는 내용은 통과

    @Test
    @DisplayName("확장자와 같은 계열 — 이미지·PDF·Office·OLE·동영상은 통과(계열 안의 차이 docx↔xlsx, mp4↔mov 는 보지 않는다)")
    void matchingFamiliesPass() throws IOException {
        passes(blocking, png(), "a.png");
        passes(blocking, jpeg(), "photo.JPG");
        passes(blocking, png(), "a.jpg");          // 이미지끼리
        passes(blocking, pdf(), "report.pdf");
        passes(blocking, docx(), "a.docx");
        passes(blocking, docx(), "a.xlsx");        // OOXML 끼리
        passes(blocking, docx(), "a.zip");
        passes(blocking, ole2(), "old.doc");
        passes(blocking, ole2(), "old.xls");
        passes(blocking, mp4(), "clip.mp4");
        passes(blocking, mp4(), "clip.mov");
    }

    @Test
    @DisplayName("텍스트류·모르는 형식은 내용으로 판별할 수 없어 통과(한계로 수용)")
    void unknownPasses() {
        passes(blocking, text(), "a.txt");
        passes(blocking, text(), "a.csv");
        passes(blocking, text(), "a.png");          // 이미지라고 했지만 텍스트 — 형식을 단정할 수 없다
        passes(blocking, "<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8), "a.html");
        passes(blocking, png(), "noext");           // 확장자 없음
        passes(blocking, png(), null);
    }

    // ------------------------------------------------------------------ 위장은 거절

    @ParameterizedTest(name = "{0} 내용을 {1} 로")
    @CsvSource({
            "pdf, a.jpg",
            "png, a.pdf",
            "docx, a.png",
            "pdf, a.docx",
            "mp4, a.pdf",
    })
    @DisplayName("확장자와 다른 계열 — 거절")
    void mismatchRejected(String kind, String fileName) throws IOException {
        byte[] content = switch (kind) {
            case "pdf" -> pdf();
            case "png" -> png();
            case "docx" -> docx();
            case "mp4" -> mp4();
            default -> throw new IllegalArgumentException(kind);
        };
        rejects(blocking, content, fileName);
    }

    @Test
    @DisplayName("다른 확장자로 위장한 실행 파일은 설정과 상관없이 거절")
    void disguisedExecutableRejected() {
        rejects(blocking, pe(), "invoice.pdf");
        rejects(blocking, pe(), "photo.jpg");
        rejects(blocking, elf(), "report.docx");
        rejects(allowingExe, pe(), "invoice.pdf");
        rejects(allowingExe, elf(), "notes.txt");   // 텍스트라고 한 실행 파일도
    }

    @Test
    @DisplayName("실행 파일 그대로(.exe·확장자 없음·.bin) — 기본은 거절, block-executable-content=false 면 통과")
    void executableByPolicy() {
        rejects(blocking, pe(), "setup.exe");
        rejects(blocking, elf(), "ls");
        passes(allowingExe, pe(), "setup.exe");
        passes(allowingExe, elf(), "ls");          // 리눅스 실행 파일은 보통 확장자가 없다
        passes(allowingExe, elf(), "firmware.bin"); // 형식을 단정하지 않는 이름
    }

    // ------------------------------------------------------------------ 스토리지 읽기

    @Test
    @DisplayName("스토리지에서 앞 64KB 만 읽는다(작은 파일은 크기만큼), 0바이트는 읽지 않는다")
    void readsOnlyTheHead() {
        given(storage.readRange(anyString(), anyLong(), anyLong())).willReturn(png());

        blocking.verify("1/key", "a.png", 10L * 1024 * 1024);
        verify(storage).readRange("1/key", 0, UploadContentInspector.SNIFF_BYTES - 1);

        blocking.verify("1/small", "a.png", 300);
        verify(storage).readRange("1/small", 0, 299);

        Mockito.clearInvocations(storage);
        blocking.verify("1/empty", "a.png", 0);
        verify(storage, never()).readRange(anyString(), anyLong(), anyLong());
    }
}
