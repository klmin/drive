package com.thinkfree.drive.recent.service;

import com.thinkfree.drive.recent.data.RecentResourceShareData;
import com.thinkfree.drive.recent.query.RecentListQuery;

import java.util.List;

public interface RecentFindService {
    
    List<RecentResourceShareData> findAll(RecentListQuery query);

}



