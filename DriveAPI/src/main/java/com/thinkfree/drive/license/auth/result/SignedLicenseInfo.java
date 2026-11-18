package com.thinkfree.drive.license.auth.result;

import java.util.HashMap;
import java.util.Map;

/**
 * 인증 서버가 서명해 준 원장 정보.
 * 서명 안의 subject 는 {@code licenseId:..|startDate:yyyyMMdd|endDate:yyyyMMdd|addFlag:..|licenseType:..} 형식이다.
 */
public record SignedLicenseInfo
(
    Long licenseId,
    String startDate,
    String endDate,
    String addFlag,
    String licenseType
)
{
    /** 라이선스를 찾지 못한 실패 응답은 빈 문자열에 서명하므로 null 을 돌려준다. */
    public static SignedLicenseInfo parse(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        for (String part : subject.split("\\|")) {
            int colon = part.indexOf(':');
            if (colon > 0) {
                values.put(part.substring(0, colon), part.substring(colon + 1));
            }
        }

        String licenseId = values.get("licenseId");
        return new SignedLicenseInfo(
                licenseId == null || licenseId.isBlank() ? null : Long.valueOf(licenseId),
                emptyToNull(values.get("startDate")),
                emptyToNull(values.get("endDate")),
                values.getOrDefault("addFlag", ""),
                emptyToNull(values.get("licenseType")));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
