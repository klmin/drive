package com.thinkfree.drive.api.open.branding.response;

public record BrandingImageResponse
(
    byte[] data,
    String contentType,
    long contentLength,
    String etag,
    boolean notModified
)
{
    public static BrandingImageResponse notModified(String etag) {
        return new BrandingImageResponse(null, null, 0, etag, true);
    }
}
