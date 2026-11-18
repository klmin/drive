package com.thinkfree.drive.trash.service;

import com.thinkfree.drive.trash.dto.TrashSaveDto;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;

import java.util.List;

public interface TrashService {
    
    void save(TrashSaveDto dto);
    void delete(TrashDeleteQuery dto);
    void deleteByUserSeqList(List<Integer> userSeqList);
}
