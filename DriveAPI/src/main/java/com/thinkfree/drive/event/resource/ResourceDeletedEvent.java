package com.thinkfree.drive.event.resource;

import java.util.List;

public record ResourceDeletedEvent
(
        List<String> deleteKeys
)
{
}
