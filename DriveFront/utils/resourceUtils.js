/** 공유받은 파일/폴더 */
// ss 추출
export const getShareSeq = item => {
    if (!item) return '';
    return item.sharedByUserSeq || item.userSeq || '';
};

/** hasLockedFile */
export const hasLockedFile = checkItems => {
    const files = (checkItems || []).filter(item => item && item.resourceType === 'FILE');

    return files.length === 0
        ? false // 전부 폴더이면 false
        : files.some(f => f.isLocked === true); // 파일 중 하나라도 true면 true
};

/** 선택 항목 기준 동작별 비활성 여부 (컨텍스트 메뉴·키보드 단축키 공통) */
export const getResourceActionDisabled = items => {
    const list = items || [];
    const isLocked = hasLockedFile(list);
    const hasCannotEdit = list.some(i => !i?.canEdit);

    return {
        disablePreview: list.some(i => !i?.canView),
        disableDownload: list.some(i => !i?.canDownload),
        disableRename: hasCannotEdit || isLocked,
        disableDelete: list.some(i => !i?.canDelete) || isLocked,
        disableCopy: hasCannotEdit,
    };
};

/** 공유 받은 리소스인 지 (= 공유 문서함 루트에 있지 않은 폴더)*/
// FileMoveDialog에서 최근/중요/공유 문서함, 검색 결과에서 '이동' 시도할 때, 사용
export const hasShareResource = checkItems => {
    const hasSharedLocationItem = checkItems.some(i => i?.resourceLocation === 'SHARE');
    const hasNotNullFullPathItem = checkItems.some(i => i?.fullPath != null); // null이 아니면 상위 경로 존재 -> 상위 폴더(공유문서함 루트 폴더) 하위에 있음

    return hasSharedLocationItem && hasNotNullFullPathItem;
};

/** 공유 문서함 루트에 있는 리소스가 포함 여부
 * 포함 되었다면 '이동' 불가능*
 */
// 최근/중요 문서함, 검색 결과에서 사용 (컨텍스트 메뉴 및 액션 아이콘 메뉴)
export const hasSharedRootResource = items => {
    const hasSharedLocationItem = items.some(i => i?.resourceLocation === 'SHARE');
    const hasNullFullPathItem = items.some(i => i?.fullPath == null);

    return hasSharedLocationItem && hasNullFullPathItem;
};

/** 타켓 폴더가 내 드라이브에 있는 리소스인 지
 * (소유자 나인 경우도, 내가 아닌 경우도 존재(공유 문서함 -> 내드라이브로 이동한 경우 소유자는 남일 수 있음))
 * */
// 공유받은 폴더/파일 -> 타켓 폴더 이동 시 내드라이브로 이동인지 판별 위해 사용
export const isMyDriveOwnedItem = (targetItem, loginUser) => {
    if (targetItem?.userSeq) {
        return targetItem?.userSeq === loginUser.userSeq; // 폴더가 있는 곳의 주인 (= userSeq)
    }
    return true;
};

/** 내드라이브 내에 있는 리소스인 지 */
export const hasMyDriveResource = items => {
    return items.some(i => i?.resourceLocation === 'MY');
};
