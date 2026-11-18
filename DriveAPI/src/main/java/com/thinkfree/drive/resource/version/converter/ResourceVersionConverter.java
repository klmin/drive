package com.thinkfree.drive.resource.version.converter;

import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.version.dto.ResourceVersionSaveDto;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.query.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring", imports = ResourceVersionStatus.class)
public interface ResourceVersionConverter {
    
    ResourceVersionSaveDto toResourceVersionSaveDto(Integer userSeq, Long resourceSeq, Integer ownerSeq, String resourceSaveName, Integer versionNumber, Long sizeByte, Instant versionRegisterDate, Integer registerUserSeq);
    ResourceVersion toSaveResourceVersion(ResourceVersionSaveDto dto, ResourceVersionStatus status);
    ResourceVersionDeleteQuery toDeleteResourceVersionQuery(Long resourceVersionSeq, Long resourceSeq, int userSeq);
    @Mapping(target = "tempOffset", constant = "100000")
    ResourceVersionNumberQuery toResourceVersionDecreaseNumberQuery(Long resourceSeq, int userSeq, ResourceVersionStatus status, Integer versionNumber);
    @Mapping(target = "tempOffset", constant = "100000")
    ResourceVersionNumberQuery toResourceVersionIncreaseNumberQuery(Long resourceSeq, int userSeq, ResourceVersionStatus status, Integer versionNumber);
    
    @Mapping(target = "minute", constant = "3")
    ResourceVersionFindQuery toResourceVersionFindQuery(Long resourceSeq, int userSeq, ResourceVersionStatus status);
            
    @Mapping(target = "status", expression = "java(ResourceVersionStatus.SUCCESS)")
    ResourceVersionFindQuery toResourceVersionFindQuery(Long resourceVersionSeq, Long resourceSeq, int userSeq);
    
    ResourceVersionListQuery toResourceVersionListQuery(Long resourceSeq, int userSeq, List<Long> resourceSeqList);
    
    ResourceVersionDeleteSeqListQuery toResourceVersionDeleteSeqListQuery(List<Long> resourceSeqList, int userSeq);
    
    ResourceVersionUserUpdateQuery toResourceVersionUserUpdateQuery(Long resourceSeq, int userSeq, int changeUserSeq);
    ResourcesVersionUserUpdateQuery toResourcesVersionUserUpdateQuery(Collection<Long> resourceSeqList, int userSeq, int changeUserSeq);
    
    Resource toOverwriteResource(Long resourceSeq, int userSeq, long sizeByte);
    
    
}
