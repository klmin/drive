package com.thinkfree.drive.basedomain.converter;

import com.thinkfree.drive.basedomain.entity.BaseDomain;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BaseDomainConverter {
    
    BaseDomain toUpsertBaseDomain(String baseDomain, String scheme, Integer port, Integer baseDomainSeq);
}
