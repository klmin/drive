package com.thinkfree.drive.share.converter;

import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.result.ShareSaveResult;
import com.thinkfree.drive.share.target.result.ShareTargetSaveResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface ShareResultConverter {
    
    @Mapping(target="actorUserSeq", source = "share.registerUserSeq")
    @Mapping(target="occurredDatetime", expression = "java(share.getRegisterDate())")
    ShareSaveResult toShareSaveResult(Share share, ShareTargetSaveResult shareTargetSaveResult);
    
}
