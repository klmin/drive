package com.thinkfree.drive.share.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SharedResourceNearestData {
    private Long shareSeq;
    private ShareType shareType;
    private Long resourceSeq;
    private Long rootResourceSeq;
    private ResourceType resourceType;
}
