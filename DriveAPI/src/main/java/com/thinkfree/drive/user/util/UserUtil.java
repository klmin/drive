package com.thinkfree.drive.user.util;

import com.thinkfree.drive.user.data.UserData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserUtil {

    public static List<String> extractUserIds(Set<Integer> userSeqSet, List<UserData> userDataList) {
        if (userSeqSet.isEmpty() || userDataList.isEmpty()) {
            return Collections.emptyList();
        }
        return userDataList.stream()
                           .filter(u -> userSeqSet.contains(u.getUserSeq()))
                           .map(UserData::getUserId)
                           .collect(Collectors.toList());
    }
}
