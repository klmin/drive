package com.thinkfree.drive.event.share.target.converter;

import com.thinkfree.drive.email.dto.ShareTargetAddedEmailDto;
import com.thinkfree.drive.event.share.target.ShareTargetReplacedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShareTargetEventConverter {
    
    @Mapping(target="tenantSeq",       source="tenantSeq")
    @Mapping(target="recipientEmails", source="shareTargetUserEmails")
    ShareTargetAddedEmailDto toShareTargetAddedEmailDto(ShareTargetReplacedEvent event);
}
