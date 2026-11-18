package com.thinkfree.drive.application.resource.converter;

import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.util.zip.DownloadZipEntry;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ResourceToZipEntryConverter {

    List<DownloadZipEntry> toDownloadZipEntries(List<ResourceHierarchyData> list);

}
