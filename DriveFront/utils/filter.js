import {
    RangeDateFilter,
    RoleFilter,
    StatusFilter,
    TypeFilter,
    WithinDateFilter,
} from '@/constants/FILTER';

// modifyDate -> 'dateFilter.xxx' 키 반환
export function getRangeNameByModifyDate(modifyDate, isScheduledDate = false) {
    const start = Array.isArray(modifyDate)
        ? modifyDate[0]
        : (modifyDate?.start ?? modifyDate?.from);
    const end = Array.isArray(modifyDate) ? modifyDate[1] : (modifyDate?.end ?? modifyDate?.to);

    if (!start || !end) return 'dateFilter.custom';

    const filterList = isScheduledDate ? WithinDateFilter : RangeDateFilter;
    const found = filterList.find(({ value: [s, e] }) => s === start && e === end);
    return found ? found.name : `${start} ~ ${end}`;
}

// fileType -> 'fileTypeFilter.xxx' 키 반환
export function getFileTypeNameByValue(fileType) {
    if (!fileType) return 'fileTypeFilter.filterType'; // 기본값

    const filterList = TypeFilter;
    const found = filterList.find(it => it.value === fileType);
    return found ? found.name : 'fileTypeFilter.filterType';
}

// status -> 'statusFilter.xxx' 키 반환
export function getRoleNameByValue(role) {
    const found = RoleFilter.find(it => it.value === role);
    return found ? found.name : 'common.label.role';
}

export function getStatusNameByValue(fileType) {
    if (!fileType) return 'common.label.status'; // 기본값

    const filterList = StatusFilter;
    const found = filterList.find(it => it.value === fileType);

    return found ? found.name : 'common.label.status';
}
