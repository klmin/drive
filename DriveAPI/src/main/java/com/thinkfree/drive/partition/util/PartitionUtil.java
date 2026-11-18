package com.thinkfree.drive.partition.util;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PartitionUtil {
    
    public <T extends PartitionKeyProvider> Map<PartitionKey, T> mapByPartitionKey(Collection<T> list) {
        return list.stream()
                    .collect(Collectors.toMap(
                                d -> new PartitionKey(d.getSharedByUserSeq(), d.getResourceSeq()),
                                Function.identity()
                    ));
    }
 
}
