package com.thinkfree.drive.weboffice.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;

@Builder
@Getter
public class WebOfficeGetResult {
    private InputStream inputStream;
}
