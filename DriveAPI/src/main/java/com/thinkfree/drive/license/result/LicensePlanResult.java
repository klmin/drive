package com.thinkfree.drive.license.result;

/**
 * 라이선스 플랜 속성 스냅샷 (브랜딩 메타 등 표시용).
 * {@code LicenseService} 캐시를 한 번만 읽어 두 값을 함께 채운다 — 갱신 사이에 값이 어긋나지 않도록.
 * 라이선스 미등록 테넌트는 {@link #EMPTY}.
 *
 * @param category    라이선스 카테고리
 * @param subCategory 플랜 (서브 카테고리)
 */
public record LicensePlanResult
(
    String category,
    String subCategory
)
{
    public static final LicensePlanResult EMPTY = new LicensePlanResult("", "");
}
