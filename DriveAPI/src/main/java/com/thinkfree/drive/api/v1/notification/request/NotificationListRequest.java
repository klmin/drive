package com.thinkfree.drive.api.v1.notification.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationListRequest(
        @Schema(title = "Read state", description = "Use true for read notifications or false for unread notifications. Leave unset to include both.")
        Boolean ir
) {
}
