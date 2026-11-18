package com.thinkfree.drive.sso.provider.service;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.factory.SsoProtocolQueryFactory;
import com.thinkfree.drive.sso.protocol.registry.SsoProtocolRegistry;
import com.thinkfree.drive.sso.provider.converter.SsoProviderConverter;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.exception.SsoProviderErrorCode;
import com.thinkfree.drive.sso.provider.query.SsoProviderListQuery;
import com.thinkfree.drive.sso.provider.repository.SsoProviderQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultSsoProviderFindService implements SsoProviderFindService{

    private final SsoProviderConverter ssoProviderConverter;
    private final SsoProviderQueryRepository ssoProviderQueryRepository;
    private final SsoProtocolRegistry ssoProtocolRegistry;
    private final SsoProtocolQueryFactory ssoProtocolQueryFactory;

    @Override
    public List<SsoProviderData> findAll(SsoProviderListQuery query) {
        return ssoProviderQueryRepository.findAll(query);
    }

    @Override
    public SsoProviderData findById(int ssoProviderSeq) {
        var ssoProviderFindQuery = ssoProviderConverter.toSsoProviderFindQuery(ssoProviderSeq);
        var data = ssoProviderQueryRepository.findById(ssoProviderFindQuery);
        DriveAssert.throwIfNull(data, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
        return data;
    }

    @Override
    public SsoProviderDetailData findDetailById(int ssoProviderSeq) {
        var ssoProviderData = this.findById(ssoProviderSeq);
        var protocolFindService = ssoProtocolRegistry.getFind(ssoProviderData.getProtocolType());
        var protocolFindQuery = ssoProtocolQueryFactory.createFindQuery(ssoProviderData.getSsoProviderSeq(), ssoProviderData.getProtocolType());
        var protocolData = protocolFindService.findById(protocolFindQuery);
        return ssoProviderConverter.toSsoProviderDetailData(ssoProviderData, protocolData);
    }

    @Override
    public List<SsoProviderDetailData> findAllDetailsByTenantAndProtocol(int tenantSeq, SsoProviderProtocolType protocolType) {

        var listQuery = ssoProviderConverter.toSsoProviderListQuery(tenantSeq, protocolType);
        var providers = ssoProviderQueryRepository.findAll(listQuery);

        return toDetails(providers, protocolType);
    }

    @Override
    public List<SsoProviderDetailData> findAllGlobalDetailsByProtocol(SsoProviderProtocolType protocolType) {

        var listQuery = ssoProviderConverter.toSsoProviderGlobalListQuery(protocolType);
        var providers = ssoProviderQueryRepository.findAllGlobal(listQuery);

        return toDetails(providers, protocolType);
    }

    @Override
    public boolean existsActiveSingleType(int tenantSeq, SsoProviderProtocolType protocolType) {
        var query = ssoProviderConverter.toSsoProviderActiveCheckQuery(tenantSeq, protocolType);
        return ssoProviderQueryRepository.existsActiveSingleType(query);
    }

    @Override
    public boolean existsActiveSingleTypeByGlobal(SsoProviderProtocolType protocolType) {
        var query = ssoProviderConverter.toSsoProviderGlobalActiveCheckQuery(protocolType);
        return ssoProviderQueryRepository.existsActiveSingleTypeByGlobal(query);
    }

    @Override
    public boolean existsDisplayName(SsoProviderScope scope, Integer tenantSeq, String displayName, Integer excludeSsoProviderSeq) {
        var query = ssoProviderConverter.toSsoProviderDisplayNameCheckQuery(scope, tenantSeq, displayName, excludeSsoProviderSeq);
        return ssoProviderQueryRepository.existsDisplayName(query);
    }

    @Override
    public SsoLoginConfigData findActiveByTenantAndProtocolOrThrow(int tenantSeq, SsoProviderProtocolType protocolType) {
        var provider = findActiveByTenantAndProtocolOrNull(tenantSeq, protocolType);
        DriveAssert.throwIfNull(provider, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
        return loadLoginConfig(provider.getSsoProviderSeq(), protocolType);
    }

    @Override
    public SsoLoginConfigData findActiveByGlobalAndProtocolOrThrow(SsoProviderProtocolType protocolType) {
        var provider = findActiveByGlobalAndProtocolOrNull(protocolType);
        DriveAssert.throwIfNull(provider, SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
        return loadLoginConfig(provider.getSsoProviderSeq(), protocolType);
    }

    @Override
    public SsoLoginConfigData findLoginConfigByProtocolOrNull(int tenantSeq, SsoProviderProtocolType protocolType) {
        var provider = findActiveByTenantAndProtocolOrNull(tenantSeq, protocolType);
        if (provider == null) {
            return null;
        }
        return loadLoginConfig(provider.getSsoProviderSeq(), protocolType);
    }

    @Override
    public SsoLoginConfigData findLoginConfigByGlobalAndProtocolOrNull(SsoProviderProtocolType protocolType) {
        var provider = findActiveByGlobalAndProtocolOrNull(protocolType);
        if (provider == null) {
            return null;
        }
        return loadLoginConfig(provider.getSsoProviderSeq(), protocolType);
    }

    private SsoProviderData findActiveByTenantAndProtocolOrNull(int tenantSeq, SsoProviderProtocolType protocolType) {
        var query = ssoProviderConverter.toSsoProviderByProtocolQuery(tenantSeq, protocolType, SsoProviderStatus.ACTIVE);
        return ssoProviderQueryRepository.findActiveByTenantAndProtocol(query);
    }

    private SsoProviderData findActiveByGlobalAndProtocolOrNull(SsoProviderProtocolType protocolType) {
        var query = ssoProviderConverter.toSsoProviderGlobalByProtocolQuery(protocolType, SsoProviderStatus.ACTIVE);
        return ssoProviderQueryRepository.findActiveByGlobalAndProtocol(query);
    }

    private List<SsoProviderDetailData> toDetails(List<SsoProviderData> providers, SsoProviderProtocolType protocolType) {
        if (providers.isEmpty()) {
            return List.of();
        }

        var ssoProviderSeqList = providers.stream().map(SsoProviderData::getSsoProviderSeq).toList();
        var configQuery = ssoProviderConverter.toSsoProtocolBySeqListQuery(ssoProviderSeqList);
        var protocolFindService = ssoProtocolRegistry.getFind(protocolType);
        var configMap = protocolFindService.findAllByProviderSeqList(configQuery).stream().collect(Collectors.toMap(SsoProtocolData::getSsoProviderSeq, Function.identity()));

        return providers.stream()
                        .map(provider -> ssoProviderConverter.toSsoProviderDetailData(provider, configMap.get(provider.getSsoProviderSeq())))
                        .toList();
    }

    private SsoLoginConfigData loadLoginConfig(int ssoProviderSeq, SsoProviderProtocolType protocolType) {
        var protocolFindService = ssoProtocolRegistry.getFind(protocolType);
        var findQuery = ssoProtocolQueryFactory.createFindQuery(ssoProviderSeq, protocolType);
        return protocolFindService.findLoginConfig(findQuery);
    }

}
