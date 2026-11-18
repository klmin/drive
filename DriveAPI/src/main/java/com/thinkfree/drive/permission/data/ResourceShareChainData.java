package com.thinkfree.drive.permission.data;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 리소스 1건의 공유 사슬 — chains 는 depth 0(리소스 자신)부터 조상 순. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceShareChainData {

    private Long resourceSeq;
    private Integer userSeq;
    private List<ResourceShareChainItem> chains;

}
