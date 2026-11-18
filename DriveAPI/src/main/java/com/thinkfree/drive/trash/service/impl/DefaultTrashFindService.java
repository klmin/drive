package com.thinkfree.drive.trash.service.impl;

import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.query.TrashListQuery;
import com.thinkfree.drive.trash.repository.TrashQueryRepository;
import com.thinkfree.drive.trash.service.TrashFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultTrashFindService implements TrashFindService {
    
    private final TrashQueryRepository trashQueryRepository;
    
    public List<TrashData> findAll(TrashListQuery query){
        return trashQueryRepository.findAll(query);
    }
}
