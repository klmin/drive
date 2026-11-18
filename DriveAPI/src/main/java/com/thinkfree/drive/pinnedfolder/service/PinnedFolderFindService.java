package com.thinkfree.drive.pinnedfolder.service;

import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import com.thinkfree.drive.pinnedfolder.exception.PinnedFolderErrorCode;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderExistsQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderListQuery;
import com.thinkfree.drive.pinnedfolder.repository.PinnedFolderQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PinnedFolderFindService {
    
    private final PinnedFolderQueryRepository pinnedFolderQueryRepository;
    
    public void existsPinnedFolder(PinnedFolderExistsQuery query){
        var result = pinnedFolderQueryRepository.existsPinnedFolder(query);
        DriveAssert.throwIfTrue(result, PinnedFolderErrorCode.CONFLICT);
    }
    
    public List<PinnedFolderData> findPinnedFolders(PinnedFolderListQuery query){
        return pinnedFolderQueryRepository.findPinnedFolders(query);
    }
    
}
