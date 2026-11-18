package com.thinkfree.drive.notification.planner;

import com.thinkfree.drive.notification.event.enums.ActionType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationPlannerRegistry {
    
    private final Map<ActionType, NotificationPlanner> map;

    public NotificationPlannerRegistry(List<NotificationPlanner> planner) {
        var map = new EnumMap<ActionType, NotificationPlanner>(ActionType.class);
        for (var c : planner) {
            var key = c.supports();
            var prev = map.putIfAbsent(key, c);
            if (prev != null) {
                throw new IllegalStateException("Duplicate planner for " + key);
            }
        }
        this.map = Collections.unmodifiableMap(map);
    }

    public NotificationPlanner get(ActionType type) {
        var c = map.get(type);
        if (c == null) {
            throw new IllegalArgumentException("No planner for " + type);
        }
        return c;
    }
}
