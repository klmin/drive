package com.thinkfree.drive.trash.service.impl;

import com.thinkfree.drive.trash.converter.TrashConverter;
import com.thinkfree.drive.trash.dto.TrashSaveDto;
import com.thinkfree.drive.trash.exception.TrashErrorCode;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;
import com.thinkfree.drive.trash.repository.TrashRepository;
import com.thinkfree.drive.trash.service.TrashService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultTrashService implements TrashService {
    
    private final TrashRepository trashRepository;
    private final TrashConverter trashConverter;

    @Override
    public void save(TrashSaveDto dto) {
        var entity = trashConverter.toSaveTrash(dto);
        trashRepository.save(entity);
    }

    @Override
    public void delete(TrashDeleteQuery query) {
        var result = trashRepository.delete(query);
        DriveAssert.throwIfZero(result, TrashErrorCode.DELETE_FAIL);
    }
    
    @Override
    public void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = trashRepository.deleteByUserSeqList(userSeqList);
        log.info("[휴지통 삭제 : {}건]", result);
    }
}
