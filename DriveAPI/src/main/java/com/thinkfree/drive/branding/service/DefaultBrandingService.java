package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.converter.BrandingConverter;
import com.thinkfree.drive.branding.dto.BrandingUpsertDto;
import com.thinkfree.drive.branding.repository.BrandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultBrandingService implements BrandingService{
    
    private final BrandingConverter brandingConverter;
    private final BrandingRepository brandingRepository;

    public void upsert(BrandingUpsertDto dto) {
        var entity = brandingConverter.toBrandingUpsert(dto);
        if (dto.isUseDefault()) {
            brandingRepository.upsertTextOnly(entity);
            return;
        }
        brandingRepository.upsertWithImages(entity);
        
    }
    @Override
    public void deleteByTenant(Integer tenantSeq) {
        brandingRepository.deleteByTenant(tenantSeq);
    }
}
