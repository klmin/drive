package com.thinkfree.drive.resource.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePathData {
    private Long resourceSeq;
    private Integer userSeq;
}
