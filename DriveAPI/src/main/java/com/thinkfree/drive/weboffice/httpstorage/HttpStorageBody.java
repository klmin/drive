package com.thinkfree.drive.weboffice.httpstorage;

import org.springframework.http.HttpStatus;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Validates a one-shot upload before any Drive resource or quota is changed. */
public final class HttpStorageBody implements AutoCloseable {
    public static final long MAX_FILE_BYTES = 300L * 1024 * 1024;
    private final Path file;
    private final byte[] json;
    private final long length;
    private final String sha256;

    private HttpStorageBody(Path file, byte[] json, long length, String sha256) {
        this.file = file; this.json = json; this.length = length; this.sha256 = sha256;
    }

    public static HttpStorageBody read(InputStream input, long expected, boolean upload) throws IOException {
        long limit = upload ? MAX_FILE_BYTES : 16 * 1024;
        if (expected > limit) throw new HttpStorageException(HttpStatus.PAYLOAD_TOO_LARGE);
        Path staged = null;
        try {
            if (upload) {
                Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
                // Windows does not support POSIX creation attributes. Check the actual temp
                // filesystem; keep owner-only permissions on POSIX and native ACLs elsewhere.
                staged = Files.getFileStore(tempDirectory).supportsFileAttributeView(PosixFileAttributeView.class)
                        ? Files.createTempFile(tempDirectory, "drive-tfo-", ".upload",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                        : Files.createTempFile(tempDirectory, "drive-tfo-", ".upload");
            }
            ByteArrayOutputStream small = upload ? null : new ByteArrayOutputStream();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long length = 0;
            try (OutputStream output = upload ? Files.newOutputStream(staged) : small) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    length += read;
                    if (length > expected || length > limit) throw HttpStorageException.unauthorized();
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (length != expected) throw HttpStorageException.unauthorized();
            return new HttpStorageBody(staged, upload ? null : small.toByteArray(), length,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        } catch (IOException | RuntimeException exception) {
            if (staged != null) Files.deleteIfExists(staged);
            throw exception;
        }
    }

    public long length() { return length; }
    public String sha256() { return sha256; }
    public byte[] json() { return json; }
    public InputStream open() throws IOException { return Files.newInputStream(file); }
    @Override public void close() throws IOException { if (file != null) Files.deleteIfExists(file); }
}
