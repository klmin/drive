package com.thinkfree.drive.sso.provider.converter;

import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolBySeqListQuery;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.dto.SsoProviderUpsertDto;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.query.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {SsoProviderStatus.class})
public interface SsoProviderConverter {

    @Mapping(target = "sortOrder", expression = "java(dto.getSortOrder() == null ? 0 : dto.getSortOrder())")
    SsoProvider toUpsertSsoProvider(SsoProviderUpsertDto dto);

    SsoProviderFindQuery toSsoProviderFindQuery(Integer ssoProviderSeq);

    SsoProviderDetailData toSsoProviderDetailData(SsoProviderData ssoProviderData, SsoProtocolData ssoProtocolData);
    SsoProviderActiveCheckQuery toSsoProviderActiveCheckQuery(Integer tenantSeq, SsoProviderProtocolType protocolType);
    SsoProviderGlobalActiveCheckQuery toSsoProviderGlobalActiveCheckQuery(SsoProviderProtocolType protocolType);
    SsoProviderDisplayNameCheckQuery toSsoProviderDisplayNameCheckQuery(SsoProviderScope scope, Integer tenantSeq, String displayName, Integer excludeSsoProviderSeq);

    SsoProviderListQuery toSsoProviderListQuery(Integer tenantSeq, SsoProviderProtocolType protocolType);
    SsoProviderGlobalListQuery toSsoProviderGlobalListQuery(SsoProviderProtocolType protocolType);

    default SsoProtocolBySeqListQuery toSsoProtocolBySeqListQuery(List<Integer> ssoProviderSeqList){
        return SsoProtocolBySeqListQuery.builder()
                                        .ssoProviderSeqList(ssoProviderSeqList)
                                        .build();
    }

    SsoProviderByProtocolQuery toSsoProviderByProtocolQuery(Integer tenantSeq, SsoProviderProtocolType protocolType, SsoProviderStatus status);
    SsoProviderGlobalByProtocolQuery toSsoProviderGlobalByProtocolQuery(SsoProviderProtocolType protocolType, SsoProviderStatus status);

}
