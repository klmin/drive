package com.thinkfree.drive.resource.model;

import com.thinkfree.drive.util.ResourceUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class NumberedResourceName{

    public static final int FIRST_NUMBER = 0;
    public static final int FIRST_INCREMENTED_NUMBER = 1;

    private final String resourceName;
    private final String originalFileName;
    private final int copyNumber;

    /**
     * 최초 생성 - 원본 파일명을 그대로 사용.
     *
     * @param baseFileName    기본 파일명
     * @param originalFileName 기본 파일명
     * @return 원본 파일명 그대로 반환
     */
    public static NumberedResourceName initial(String baseFileName, String originalFileName) {
        return new NumberedResourceName(baseFileName, originalFileName, FIRST_NUMBER);
    }

    /**
     * 최초 생성 - 복사본 이름 형식을 따른다.
     *
     * @param baseFileName    복사본의 기본 파일명
     * @param originalFileName 기본 파일명에서 넘버링 제거
     * @param copyNumber       복사본의 번호
     * @return 복사본 명칭이 적용된 파일명
     */
    public static NumberedResourceName initialCopyWithNumber(String baseFileName, String originalFileName, int copyNumber) {
        return new NumberedResourceName(baseFileName, originalFileName, copyNumber);
    }

    /**
     * 최초 복사본 생성 - 복사본 번호를 자동으로 추가.
     *
     * @param ext             파일 확장자
     * @param baseFileName     복사본의 기본 파일명
     * @param originalFileName 기본 파일명
     * @return 최초 복사본으로 파일명을 설정
     */
    public static NumberedResourceName firstCopy(String ext, String baseFileName, String originalFileName) {
        return incrementedCopyWithExtension(ext, baseFileName, originalFileName, FIRST_INCREMENTED_NUMBER);
    }

    /**
     * 복사본 생성 - 기존 복사본의 번호를 기반으로 새 복사본을 생성합니다.
     *
     * @param ext             파일 확장자
     * @param baseFileName     복사본의 기본 파일명
     * @param originalFileName 기본 파일명
     * @param previousCopyNumber 이전 복사본 번호
     * @return 새 복사본 명칭이 적용된 파일명
     */
    public static NumberedResourceName incrementedCopy(String ext, String baseFileName, String originalFileName, int previousCopyNumber) {
        return incrementedCopyWithExtension(ext, baseFileName, originalFileName, previousCopyNumber + FIRST_INCREMENTED_NUMBER);
    }

    /**
     * 복사본 번호를 포함한 파일명을 생성합니다.
     *
     * @param ext             파일 확장자
     * @param numberFileName   복사본의 기본 파일명
     * @param originalFileName 기본 파일명
     * @param copyNumber       복사본 번호
     * @return 복사본 번호가 적용된 파일명
     */
    private static NumberedResourceName incrementedCopyWithExtension(String ext, String numberFileName, String originalFileName, int copyNumber) {
        var extension = ResourceUtil.generateExtension(ext);
        var numberedName = String.format("%s (%d)%s", numberFileName, copyNumber, extension);
        return new NumberedResourceName(numberedName, originalFileName, copyNumber);
    }

}
