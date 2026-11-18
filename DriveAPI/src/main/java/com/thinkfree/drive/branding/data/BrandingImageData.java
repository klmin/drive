package com.thinkfree.drive.branding.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브랜딩 이미지 1종(bytea + content type) — 구 BrandingMapper.findBrandingImage. JdbcClient 행 매퍼가 전체 인자 생성자로 만든다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BrandingImageData {
    private byte[] imageData;
    private String contentType;
}
