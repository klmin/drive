package com.thinkfree.drive.branding.result;

public record BrandingImageResult
(
    byte[] imageData,
    String contentType
)
{
    public long contentLength() {
        return imageData.length;
    }
}
