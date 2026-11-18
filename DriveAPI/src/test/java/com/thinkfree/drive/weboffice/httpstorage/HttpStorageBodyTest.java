package com.thinkfree.drive.weboffice.httpstorage;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class HttpStorageBodyTest {
    @Test void uploadRetainsContentHashPermissionsAndDeletesOnClose() throws Exception {
        byte[] bytes = "Office save".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = HttpStorageBody.read(new ByteArrayInputStream(bytes), bytes.length, true);
        var field = HttpStorageBody.class.getDeclaredField("file");
        field.setAccessible(true);
        Path file = (Path) field.get(body);
        try (body; var input = body.open()) {
            assertArrayEquals(bytes, input.readAllBytes());
            assertEquals(bytes.length, body.length());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), body.sha256());
            if (Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class)) {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
            }
        }
        assertFalse(Files.exists(file));
    }
    @Test void rejectsTruncatedAndOversizedBodies() {
        assertThrows(HttpStorageException.class, () -> HttpStorageBody.read(new ByteArrayInputStream(new byte[1]), 2, true));
        assertThrows(HttpStorageException.class, () -> HttpStorageBody.read(new ByteArrayInputStream(new byte[2]), 1, true));
        assertThrows(HttpStorageException.class, () -> HttpStorageBody.read(InputStream.nullInputStream(), HttpStorageBody.MAX_FILE_BYTES + 1, true));
    }
}
