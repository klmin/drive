package com.thinkfree.drive.sso.protocol.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SsoProtocolBySeqListQuery {
    private List<Integer> ssoProviderSeqList;
}
