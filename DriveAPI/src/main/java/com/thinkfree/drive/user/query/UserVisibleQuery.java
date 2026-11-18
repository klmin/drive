package com.thinkfree.drive.user.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UserVisibleQuery {
    private List<Integer> userSeqList;
}
