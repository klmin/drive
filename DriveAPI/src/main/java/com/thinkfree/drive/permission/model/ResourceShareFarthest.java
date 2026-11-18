package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.util.ObjectUtil;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Builder(access = AccessLevel.PRIVATE)
@Getter
public class ResourceShareFarthest implements PartitionKeyProvider {
    
    private Integer userSeq;
    private Long resourceSeq;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String fullPathSeq;
    private String fullPath;
    
    public static ResourceShareFarthest noDirectShared(int userSeq, long resourceSeq){
        return ResourceShareFarthest.builder()
                                    .userSeq(userSeq)
                                    .resourceSeq(resourceSeq)
                                    .parentFolderSeq(null)
                                    .parentFolderName(null)
                                    .fullPathSeq(null)
                                    .fullPath(null)
                                    .build();
    }
    
    public static ResourceShareFarthest from(ResourceShareChainItem chainItem) {
        // 자르는 기준은 '공유가 걸린 조상' 자체다(ancestorResourceSeq).
        // farthestShareResourceSeq(=대상자 행으로 조인한 폴더)는 링크 공유에서 항상 null 이라 기준으로 쓸 수 없다.
        var shareRootResourceSeq = chainItem.getAncestorResourceSeq();

        // 가장 바깥 공유 조상이 리소스 자신이면(직접 공유 루트) 위로 보여줄 경로 없음 → 전부 null
        var farthestShareResourceSeq = chainItem.getResourceSeq().equals(shareRootResourceSeq) ? null : shareRootResourceSeq;
        Long parentFolderSeq = ObjectUtil.valueIfNotNull(farthestShareResourceSeq, chainItem.getParentFolderSeq());
        String parentFolderName = ObjectUtil.valueIfNotNull(farthestShareResourceSeq, chainItem.getParentFolderName());

        // 경로를 'farthest 조상'부터 잘라 그 위(소유자 사적 경로)는 숨긴다.
        // 폴더 '이름'은 중복될 수 있어 marker substring 으로 자르면 더 바깥의 동명 폴더에 잘못 매치된다.
        // → seq 는 세그먼트마다 유일하므로 fullPathSeq 에서 farthest 의 세그먼트 인덱스를 찾고, 이름 경로도 같은 인덱스에서 자른다.
        int cutIndex = farthestShareResourceSeq == null ? -1 : segmentIndex(chainItem.getFullPathSeq(), String.valueOf(farthestShareResourceSeq));
        String fullPathSeq = sliceFrom(chainItem.getFullPathSeq(), cutIndex);
        String fullPath = sliceFrom(chainItem.getFullPath(), cutIndex);

        return ResourceShareFarthest.builder()
                                    .userSeq(chainItem.getUserSeq())
                                    .resourceSeq(chainItem.getResourceSeq())
                                    .parentFolderSeq(parentFolderSeq)
                                    .parentFolderName(parentFolderName)
                                    .fullPathSeq(fullPathSeq)
                                    .fullPath(fullPath)
                                    .build();
    }
    
    public static ResourceShareFarthest forOwner(ResourceShareChainItem chainItem){
        return ResourceShareFarthest.builder()
                                    .userSeq(chainItem.getUserSeq())
                                    .resourceSeq(chainItem.getResourceSeq())
                                    .parentFolderSeq(chainItem.getParentFolderSeq())
                                    .parentFolderName(chainItem.getParentFolderName())
                                    .fullPathSeq(chainItem.getFullPathSeq())
                                    .fullPath(chainItem.getFullPath())
                                    .build();
    }
    
    /** "/" 로 구분된 path 에서 segment 와 정확히 일치하는 세그먼트의 인덱스(0-base). 없으면 -1. */
    private static int segmentIndex(String path, String segment) {
        if(path == null){
            return -1;
        }
        String[] parts = path.split(DriveConstants.PATH_SEPARATOR);
        for(int i = 0; i < parts.length; i++){
            if(parts[i].equals(segment)){
                return i;
            }
        }
        return -1;
    }

    /** index 세그먼트부터 끝까지를 다시 "/" 로 잇고 마지막에 "/" 를 붙여 반환(원본 trailing 형식 유지). index 가 범위를 벗어나면 null. */
    private static String sliceFrom(String path, int index) {
        if(path == null || index < 0){
            return null;
        }
        String[] parts = path.split(DriveConstants.PATH_SEPARATOR);
        if(index >= parts.length){
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for(int i = index; i < parts.length; i++){
            sb.append(parts[i]).append(DriveConstants.PATH_SEPARATOR);
        }
        return sb.toString();
    }
    
    @Override
    public Integer getSharedByUserSeq() {
        return userSeq;
    }
    
}
