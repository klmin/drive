package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.common.DriveFixtures;
import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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

import static org.assertj.core.api.Assertions.assertThat;

@DriveIntegrationTest
@Slf4j
class ResourcePermissionFindServiceTest {

    @Autowired
    public ResourcePermissionFindService resourcePermissionFindService;

    @Autowired
    private JdbcTemplate jdbc;
    
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
    
    /**
     * 소유자의 사적 폴더 private/ 아래 shared/ 를 viewer 에게 보기 권한으로 공유하고, 그 안의 doc.txt 를 viewer 가 본다.
     * 가장 바깥 공유 조상은 shared/ — 그 위(private/)는 viewer 에게 보이면 안 된다.
     */
    private record SharedTree(int owner, int viewer, long privateFolder, long sharedFolder, long doc) {}

    private SharedTree sharedTree() {
        var fixtures = DriveFixtures.of(jdbc);
        int owner = fixtures.user(DriveFixtures.DEFAULT_TENANT, RoleType.USER, "공유하는 사용자");
        int viewer = fixtures.user(DriveFixtures.DEFAULT_TENANT, RoleType.USER, "공유받는 사용자");
        long privateFolder = fixtures.folder(owner, null, "private");
        long sharedFolder = fixtures.folder(owner, privateFolder, "shared");
        long doc = fixtures.file(owner, sharedFolder, "doc.txt");
        fixtures.shareWith(owner, sharedFolder, viewer, DriveFixtures.Perm.VIEW);
        return new SharedTree(owner, viewer, privateFolder, sharedFolder, doc);
    }

    @Test
    @Transactional
    void findResourceShareFarthest(){
        var tree = sharedTree();

        var data = resourcePermissionFindService.findResourceShareFarthest(tree.viewer(), new PartitionKey(tree.owner(), tree.doc()), false);

        assertThat(data.getResourceSeq()).isEqualTo(tree.doc());
        assertThat(data.getParentFolderSeq()).isEqualTo(tree.sharedFolder());
        assertThat(data.getParentFolderName()).isEqualTo("shared");
        assertThat(data.getFullPathSeq()).isEqualTo(tree.sharedFolder() + "/");
        assertThat(data.getFullPath()).isEqualTo("shared/");
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
    @Transactional
    void findResourcePermissionWithFarthest(){
        var tree = sharedTree();

        var data = resourcePermissionFindService.findResourcePermissionWithFarthest(tree.viewer(), new PartitionKey(tree.owner(), tree.doc()), false);

        assertThat(data.getResourceSeq()).isEqualTo(tree.doc());
        assertThat(data.getSharedByUserSeq()).isEqualTo(tree.owner());
        assertThat(data.getParentFolderSeq()).isEqualTo(tree.sharedFolder());
        assertThat(data.getFullPath()).isEqualTo("shared/");
        // 공유 권한(보기만)이 하위 파일에 그대로 내려온다
        assertThat(data.getResourcePermission().getPermissions().getCanView()).isTrue();
        assertThat(data.getResourcePermission().getPermissions().getCanEdit()).isNotEqualTo(Boolean.TRUE);
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