package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceShareFarthest.from() 단위 검증 (DB/Spring 불필요한 순수 함수).
 *
 * from() 은 createResourceShareFarthest 가 고른 "가장 바깥 연속 SPECIFIC target" chain item 을 받아
 * 공유 루트 기준 상대경로(parentFolder/fullPath)를 만든다.
 *  - farthest 가 리소스 자신(= 직접 공유 루트) / null(파일 단독·미공유) → 경로 전부 null
 *  - farthest 가 상위 조상 → 그 조상부터의 상대경로
 *  - 경로 자르기는 폴더 '이름'이 아니라 'seq 세그먼트 인덱스' 기준 → 동명 폴더가 섞여도 안전
 */
class ResourceShareFarthestTest {

    /**
     * from() 이 사용하는 필드만 채운 chain item (createResourceShareFarthest 가 고른 farthest 라고 가정).
     * 자르는 기준은 공유가 걸린 조상(ancestorResourceSeq)이다 — 대상자 지정 공유는 farthestShareResourceSeq 도
     * 같은 값으로 채워지지만, 링크 공유는 그쪽이 null 이라 기준이 되지 못한다.
     */
    private ResourceShareChainItem item(long resourceSeq, Long shareRootResourceSeq,
                                        Long parentFolderSeq, String parentFolderName,
                                        String fullPathSeq, String fullPath, String farthestResourceName) {
        return ResourceShareChainItem.builder()
                                     .userSeq(859)
                                     .resourceSeq(resourceSeq)
                                     .ancestorResourceSeq(shareRootResourceSeq)
                                     .farthestShareResourceSeq(shareRootResourceSeq)
                                     .parentFolderSeq(parentFolderSeq)
                                     .parentFolderName(parentFolderName)
                                     .fullPathSeq(fullPathSeq)
                                     .fullPath(fullPath)
                                     .farthestResourceName(farthestResourceName)
                                     .build();
    }

    /** 링크 공유 row: 대상자 행이 없어 farthestShareResourceSeq 가 비고 ancestorResourceSeq 만 채워진다. */
    private ResourceShareChainItem linkItem(long resourceSeq, Long shareRootResourceSeq,
                                            Long parentFolderSeq, String parentFolderName,
                                            String fullPathSeq, String fullPath) {
        return ResourceShareChainItem.builder()
                                     .userSeq(859)
                                     .resourceSeq(resourceSeq)
                                     .ancestorResourceSeq(shareRootResourceSeq)
                                     .parentFolderSeq(parentFolderSeq)
                                     .parentFolderName(parentFolderName)
                                     .fullPathSeq(fullPathSeq)
                                     .fullPath(fullPath)
                                     .build();
    }

    @Test
    @DisplayName("링크 공유 - 대상자 행이 없어도 공유 조상 기준으로 잘린다")
    void linkShare_cutsByAncestor() {
        var item = linkItem(40109L, 40101L, 40101L, "44", "40095/40097/40101/", "22/33/44/");

        var result = ResourceShareFarthest.from(item);

        assertEquals("40101/", result.getFullPathSeq());
        assertEquals("44/", result.getFullPath());
    }

    @Test
    @DisplayName("직접 공유 루트(가장 바깥 조상 == 리소스 자신) → 경로 전부 null")
    void directShareRoot_allNull() {
        var item = item(40101L, 40101L, 40097L, "secret", "40095/40097/40101/", "a/secret/folder/", "folder");

        var result = ResourceShareFarthest.from(item);

        assertNull(result.getParentFolderSeq());
        assertNull(result.getParentFolderName());
        assertNull(result.getFullPathSeq());
        assertNull(result.getFullPath());
        assertEquals(40101L, (long) result.getResourceSeq());
    }

    @Test
    @DisplayName("공유 조상 정보 없음(ancestorResourceSeq == null) → 경로 전부 null")
    void fileDirectShare_nullFarthest_allNull() {
        var item = item(40109L, null, 40101L, "11", "40095/40097/40101/", "22/11/11/", null);

        var result = ResourceShareFarthest.from(item);

        assertNull(result.getParentFolderSeq());
        assertNull(result.getFullPathSeq());
        assertNull(result.getFullPath());
    }

    @Test
    @DisplayName("상위 조상이 공유 루트 → 그 조상부터 상대경로 (이름 고유)")
    void relativePath_uniqueNames() {
        var item = item(40109L, 40101L, 40101L, "44", "40095/40097/40101/", "22/33/44/", "44");

        var result = ResourceShareFarthest.from(item);

        assertEquals("40101/", result.getFullPathSeq());
        assertEquals("44/", result.getFullPath());
        assertEquals(40101L, (long) result.getParentFolderSeq());
        assertEquals("44", result.getParentFolderName());
    }

    @Test
    @DisplayName("동명 폴더가 섞여도 seq 인덱스로 잘라 정확 (이름 substring 버그 회귀 방지)")
    void duplicateFolderNames_cutBySeqNotName() {
        // 40097 과 40101 이 둘 다 "11" — 이름 marker 로 자르면 첫 "11/"(=40097)에 걸려 "11/11/" 가 됨
        var item = item(40109L, 40101L, 40101L, "11", "40095/40097/40101/", "22/11/11/", "11");

        var result = ResourceShareFarthest.from(item);

        assertEquals("40101/", result.getFullPathSeq());
        assertEquals("11/", result.getFullPath(), "동명 폴더 때문에 '11/11/' 로 덜 잘리면 안 됨");
    }

    @Test
    @DisplayName("다단계: 가장 바깥 조상(40097) 기준이면 그 아래 전부 포함")
    void nestedMultiLevel_fromOutermost() {
        var item = item(40109L, 40097L, 40101L, "11", "40095/40097/40101/", "22/11/11/", "11");

        var result = ResourceShareFarthest.from(item);

        assertEquals("40097/40101/", result.getFullPathSeq());
        assertEquals("11/11/", result.getFullPath());
        assertEquals(40101L, (long) result.getParentFolderSeq());
    }

    @Test
    @DisplayName("forOwner → 절대경로 그대로 (자르지 않음)")
    void forOwner_keepsAbsolutePath() {
        var item = item(40109L, null, 40101L, "11", "40095/40097/40101/", "22/11/11/", null);

        var result = ResourceShareFarthest.forOwner(item);

        assertEquals("40095/40097/40101/", result.getFullPathSeq());
        assertEquals("22/11/11/", result.getFullPath());
        assertEquals(40101L, (long) result.getParentFolderSeq());
        assertEquals("11", result.getParentFolderName());
    }
}
