package com.thinkfree.drive.starred.service;

import com.thinkfree.drive.starred.data.StarredResourceShareData;
import com.thinkfree.drive.starred.query.StarredListQuery;

import java.util.List;


public interface StarredFindService {
    
    List<StarredResourceShareData> findAll(StarredListQuery query);
}
