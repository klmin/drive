package com.thinkfree.drive.application.share.validation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public interface ShareTargetValidationItem {

    Integer userSeq();
    Boolean canView();
    Boolean canEdit();
    Boolean canDownload();
    Boolean canUpload();
    Boolean canShare();
    Boolean sendEmail();

    /**
     * 메일 발송 대상 = 이번 요청으로 새로 추가된(addedUserSeqSet) 대상 중 sendEmail 을 켠 유저.
     * ts 미제공(링크 유형 등)이거나 추가된 대상이 없으면 빈 집합.
     */
    static Set<Integer> resolveMailUserSeqSet(List<? extends ShareTargetValidationItem> items, Set<Integer> addedUserSeqSet) {
        if(items == null || items.isEmpty() || addedUserSeqSet == null || addedUserSeqSet.isEmpty()){
            return Collections.emptySet();
        }
        return items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.sendEmail()) && addedUserSeqSet.contains(item.userSeq()))
                    .map(ShareTargetValidationItem::userSeq)
                    .collect(toSet());
    }
}
