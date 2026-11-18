package com.thinkfree.drive.api.v1.resource.activitylog.request;

import com.thinkfree.drive.permission.aspect.PermissionAwareResource;
import org.springframework.web.bind.annotation.PathVariable;

public record ResourceActivityLogListRequest
        (
                @PathVariable
                Long resourceSeq,
                Integer ss
        )
        implements PermissionAwareResource
{
    @Override
    public Integer sharedByUserSeq() {
        return ss;
    }
}
