package com.thinkfree.drive.trash.service;

import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.query.TrashListQuery;

import java.util.List;

public interface TrashFindService {
    
    List<TrashData> findAll(TrashListQuery query);
}
