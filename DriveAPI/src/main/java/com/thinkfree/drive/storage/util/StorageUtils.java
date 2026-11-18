package com.thinkfree.drive.storage.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StorageUtils {
    
    public static <T> List<List<T>> partition(List<T> list, int size) {
        
        if (list == null || list.isEmpty()){
            return Collections.emptyList();
        }
        
        if (size <= 0){
            throw new IllegalArgumentException("size must be > 0");
        }
        
        int n = list.size();
        
        if (n <= size) {
            List<List<T>> result = new ArrayList<>(1);
            result.add(list);
            return result;
        }
        
        List<List<T>> result = new ArrayList<>((n + size - 1) / size);
        
        for (int i = 0; i < n; i += size) {
            int end = Math.min(i + size, n);
            result.add(list.subList(i, end));
        }
        
        return result;
    }
}
