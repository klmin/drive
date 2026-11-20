package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.permission.model.ResourceShareFarthest;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@DriveIntegrationTest
@Slf4j
class ResourcePermissionFindServiceTest {
    
    @Autowired
    public ResourcePermissionFindService resourcePermissionFindService;
    
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 21 에게 공유된 리소스 (1, 592) 없음(리소스가 공유되지 않았습니다)")
    @Test
    void findResourcePermission() {
        int userSeq = 21;
        var partitionKey = new PartitionKey(1, 592);
        var isDelete = false;
        var data = resourcePermissionFindService.findResourcePermission(userSeq, partitionKey, isDelete);
        log.info("data : {}", data);
    }

    @Test
    void findResourcesPermissionMap() {

        int userSeq = 21;
        var partitionKeys = List.of(
                    new PartitionKey(1, 592)
                );
        var isDelete = false;
        var map = resourcePermissionFindService.findResourcesPermissionMap(userSeq, partitionKeys, isDelete);

        log.info("map : {}", map);
    }
    
    @Test
    @Disabled("dev DB 데이터 의존 — 공유된 리소스 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
    void findResourceShareFarthest(){
        int userSeq = 21;
        //var partitionKey = new PartitionKey(21, 3161);
        var partitionKey = new PartitionKey(1, 3689);
        var isDelete = false;
        var data = resourcePermissionFindService.findResourceShareFarthest(userSeq, partitionKey, isDelete);
        System.out.println("data : "+data);
    }
    
    @Test
    void findResourcesShareFarthestMap(){
        
        int userSeq = 21;
        var partitionKeys = List.of(
                    new PartitionKey(1, 3689),
                    new PartitionKey(1, 3687),
                    new PartitionKey(1, 3690),
                    new PartitionKey(21, 3161)
                );
        var isDelete = false;
        var map = resourcePermissionFindService.findResourcesShareFarthestMap(userSeq, partitionKeys, isDelete);
        System.out.println("map : "+map);
        
    }
    
    @Test
    @Disabled("dev DB 데이터 의존 — 공유된 리소스 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
    void findResourcePermissionWithFarthest(){
        int userSeq = 21;
        //var partitionKey = new PartitionKey(21, 3161);
        var partitionKey = new PartitionKey(1, 3690);
        var isDelete = false;
        var data = resourcePermissionFindService.findResourcePermissionWithFarthest(userSeq, partitionKey, isDelete);
        System.out.println("data : "+data);
    }
    
     @Test
    void findResourcesWithFarthestMap(){
        int userSeq = 21;
        //var partitionKey = new PartitionKey(21, 3161);
        var partitionKeys = List.of(
                    new PartitionKey(1, 3689),
                    new PartitionKey(1, 3687),
                    new PartitionKey(1, 3690),
                    new PartitionKey(1, 3688),
                    new PartitionKey(21, 3161),
                    new PartitionKey(21, 4168)
                );
        var isDelete = false;
        var dataMap = resourcePermissionFindService.findResourcesPermissionWithFarthestMap(userSeq, partitionKeys, isDelete);
        
         dataMap.forEach((key, value) -> {
             System.out.println("key : "+key+" value : "+value);
         });
    }
    
}