/** 비거나 undefined 값은 쿼리스트링에 포함하지 않도록 처리 */
export const removeEmptyKeys = obj => {
    return Object.entries(obj).reduce((acc, [key, value]) => {
        if (value !== '' && value != null) {
            acc[key] = value;
        }
        return acc;
    }, {});
};

/** 현재 쿼리와 새 쿼리 비교 */
export const areParamsEqual = (obj1, obj2) => {
    const keys1 = Object.keys(obj1);
    const keys2 = Object.keys(obj2);
    if (keys1.length !== keys2.length) return false;

    return keys1.every(key => obj1[key] === obj2[key]);
};

/** drive */
export const parseDriveFiltersFromParams = query => {
    const defaults = {
        ss: '',
        sk: '',
        pfs: '',
        rt: '',
        st: 'MY', // 기본은 'MY'
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'name', // 기본 정렬 기준
        so: 'asc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

/** recent */
export const parseRecentFiltersFromParams = query => {
    const defaults = {
        rt: '',
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

// /** starred */
export const parseStarredFiltersFromParams = query => {
    const defaults = {
        rt: '',
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'name', // 기본 정렬 기준
        so: 'asc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

// /** shared */
export const parseSharedFiltersFromParams = query => {
    const defaults = {
        st: query.pfs ? 'MY' : '',
        rt: '',
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'name', // 기본 정렬 기준
        so: 'asc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

// /** trash */
export const parseTrashFiltersFromParams = query => {
    const defaults = {
        rt: '',
        di: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'register', // 기본 정렬 기준
        so: 'desc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

// /** search */
export const parseSearchFiltersFromParams = query => {
    const defaults = {
        sk: '',
        pfs: '',
        rt: '',
        st: 'ALL',
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'name', // 기본 정렬 기준
        so: 'asc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

/** 익명 공유 폴더 */
export const parseShareFolderFiltersFromParams = query => {
    const defaults = {
        ss: '',
        sk: '',
        pfs: '',
        rt: '',
        st: 'MY', // 기본은 'MY'
        oi: '',
        sd: '',
        ed: '',
        ft: '',
        sb: 'name', // 기본 정렬 기준
        so: 'asc', // 기본 정렬 순서
        ps: '20', // 기본 페이지 크기
        ci: '',
        cv: '',
        ct: '',
    };

    return { ...defaults, ...query };
};

/** 필터 초기화 */
export const buildClearQuery = query => {
    const params = { ...query };
    delete params.rt;
    delete params.ft;
    delete params.oi;
    delete params.sd;
    delete params.ed;
    delete params.ia;
    delete params.oin;
    delete params.s;
    delete params.di;
    delete params.din;
    delete params.pdSd;
    delete params.pdEd;
    delete params.dsSd;
    delete params.dsEd;

    return removeEmptyKeys(params);
};

/** 정렬 - 이름순, 파일크기순, 마지막수정일순, 마지막으로 열어본 날짜 */
export const buildSortedQuery = (query, sortBy, sortOrder) => {
    const params = { ...query };

    params.sb = sortBy;
    params.so = sortOrder;

    delete params.ci;
    delete params.cv;
    delete params.ct;

    const cleanedParams = removeEmptyKeys(params);
    return cleanedParams;
};

export const buildFilterQuery = (query, filterType, value, labelValue = '') => {
    const params = { ...query };

    switch (filterType) {
        case 'fileType':
            params.ft = value;
            break;

        case 'owner':
            params.oi = value;
            params.oin = labelValue;
            break;

        case 'deleteUser': // 삭제한 사용자
            params.di = value;
            params.din = labelValue;
            break;

        case 'date':
            // value [sd, ed] 배열로 받는다고 가정
            params.sd = value?.[0] || '';
            params.ed = value?.[1] || '';
            break;

        case 'status':
            params.s = value;
            break;

        case 'role':
            params.rt = value;
            break;

        case 'requestedDate':
            params.pdSd = value?.[0] || '';
            params.pdEd = value?.[1] || '';
            break;

        case 'scheduledDate':
            params.dsSd = value?.[0] || '';
            params.dsEd = value?.[1] || '';
            break;

        default:
            delete params.rt;
            delete params.oi;
            delete params.sd;
            delete params.ed;
            delete params.di;
            delete params.s;
            delete params.pdSd;
            delete params.pdEd;
            delete params.dsSd;
            delete params.dsEd;
    }

    const cleanedParams = removeEmptyKeys(params);
    return cleanedParams;
};

/** users, admins */
export const parseUsersFiltersFromParams = query => {
    const defaults = {
        ps: query.ps || '20',
        s: query.s || '',
    };

    return { ...defaults, ...query };
};

/** pending-deletion */
export const parseUsersDeletionFiltersFromParams = query => {
    const defaults = {
        ps: query.ps || '20',
        pdSd: query.pdSd || '',
        pdEd: query.pdEd || '',
        dsSd: query.dsSd || '',
        dsEd: query.dsEd || '',
    };

    return { ...defaults, ...query };
};

/** ssoProvider */
export const parseSsoProvidersFiltersFromParams = query => {
    const defaults = {
        ps: query.ps || '20',
    };

    return { ...defaults, ...query };
};

/** api-keys */
export const parseApiKeysFiltersFromParams = query => {
    const defaults = {
        ps: query.ps || '20',
    };

    return { ...defaults, ...query };
};

/**
 * auditLogs — URL 쿼리 → 화면 필터 상태.
 * 다른 parseXFiltersFromParams와 달리 API 파라미터를 그대로 통과시키지 않는다
 * (eventCategories·eventTypes는 URL엔 콤마 문자열, 화면에선 배열).
 * 빈 쿼리를 넣으면 초기 필터값이 된다.
 */
/** 콤마로 이어 담은 다중 선택 값을 배열로 */
const splitCsv = value => (value ? String(value).split(',').filter(Boolean) : []);

export const parseAuditLogFilters = query => ({
    actorEmail: query.actorEmail ?? '',
    from: query.from ?? '',
    to: query.to ?? '',
    eventCategories: splitCsv(query.eventCategories),
    eventTypes: splitCsv(query.eventTypes),
    channel: query.channel ?? '',
    outcomeStatus: query.outcomeStatus ?? '',
});

/**
 * auditLogs — 화면 필터 상태 + 페이지 → URL 쿼리. 빈 값과 1페이지는 키째 뺀다.
 * eventCategories만 API 이름과 다르고 나머지는 그대로 조회 파라미터로 쓴다.
 */
export const buildAuditLogQuery = (filters, pi) =>
    removeEmptyKeys({
        actorEmail: filters.actorEmail.trim(),
        from: filters.from,
        to: filters.to,
        eventCategories: filters.eventCategories.join(','),
        eventTypes: filters.eventTypes.join(','),
        channel: filters.channel,
        outcomeStatus: filters.outcomeStatus,
        pi: pi > 1 ? String(pi) : '',
    });
