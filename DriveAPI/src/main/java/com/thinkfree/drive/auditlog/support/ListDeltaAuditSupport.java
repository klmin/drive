package com.thinkfree.drive.auditlog.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "허용 IP 목록"처럼 diff 없이 전체 교체되는 문자열 리스트 값에서, 변경 전/후를 비교해
 * SETTING_ALLOWED_IP_CHANGE 등 델타(added/removed) 방식 이벤트의 afterValue용 Map을 만든다.
 * added/removed 둘 다 비어있으면 빈 Map(변경 없음 → 로그 미기록 신호로 사용).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ListDeltaAuditSupport {

    public static Map<String, List<String>> computeDelta(List<String> before, List<String> after) {
        List<String> added = after.stream().filter(v -> !before.contains(v)).toList();
        List<String> removed = before.stream().filter(v -> !after.contains(v)).toList();

        Map<String, List<String>> delta = new LinkedHashMap<>();
        if (!added.isEmpty()) {
            delta.put("added", added);
        }
        if (!removed.isEmpty()) {
            delta.put("removed", removed);
        }
        return delta;
    }
}
