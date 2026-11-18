package com.thinkfree.drive.api.response;

public record DownloadLinkResponse(
        String linkUrl
) {
    private static final String DOWNLOAD_TOKEN_PARAM = "dt";

    private static String buildDownloadUrl(String endPoint, String token) {
        return endPoint + "?" + DOWNLOAD_TOKEN_PARAM + "=" + token;
    }

    public static DownloadLinkResponse File(String token) {
        return new DownloadLinkResponse(buildDownloadUrl("/files/download", token));
    }

    public static DownloadLinkResponse Folder(String token) {
        return new DownloadLinkResponse(buildDownloadUrl("/folders/download", token));
    }

    public static DownloadLinkResponse Multi(String token) {
        return new DownloadLinkResponse(buildDownloadUrl("/resources/download", token));
    }

    public static DownloadLinkResponse Version(Long resourceSeq, String token) {
        return new DownloadLinkResponse(buildDownloadUrl("/resources/" + resourceSeq + "/versions/download", token));
    }
}

