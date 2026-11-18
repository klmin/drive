package com.thinkfree.drive.share.query;

import lombok.experimental.SuperBuilder;
import lombok.Getter;

@SuperBuilder
@Getter
public class SharedResourceFindQuery extends ShareDefaultFindQuery{
    private Long resourceSeq;
}
