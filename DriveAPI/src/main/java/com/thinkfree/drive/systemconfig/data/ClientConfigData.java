package com.thinkfree.drive.systemconfig.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClientConfigData {
    private Boolean intellectStatus;
    private Boolean officeStatus;
    private Boolean shareStatus;
}
