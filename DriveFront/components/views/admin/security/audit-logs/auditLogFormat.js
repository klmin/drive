/**
 * 감사 로그 목록 셀 포맷터 + 라벨 사전.
 * 라벨 사전은 코드 → 스트링 리소스 키 매핑이고, 실제 문구는 호출부에서 받은 t로 만든다.
 */
import dayjs from 'dayjs';
import convertBytesToReadableSize from '@/utils/math';
import { buildAuditLogQuery } from '@/utils/queryString';
import auditLogTokens from './auditLogTokens';

// 백엔드 EventCategory enum과 일치해야 함 (거버넌스 sourceMetadata.tfd.eventCategory 값)
export const EVENT_CATEGORIES = ['LOGIN', 'FILE_FOLDER', 'SHARE', 'USER_MANAGEMENT', 'SETTING'];
export const CHANNELS = ['WEB', 'API', 'MCP', 'SYSTEM'];
export const OUTCOMES = ['SUCCESS', 'FAIL'];

// 백엔드 EventName enum을 카테고리별로 묶은 목록(이벤트 다중 선택용).
// 주의: 로그인 3종은 서버에 LOGIN으로 저장돼 개별 이름으로는 안 잡힐 수 있다.
export const EVENT_TYPE_GROUPS = [
    { category: 'LOGIN', types: ['LOGIN', 'LOGOUT', 'ACCOUNT_LOCKED_STATUS_CHANGE'] },
    {
        category: 'FILE_FOLDER',
        types: [
            'FILE_CREATE',
            'FILE_UPLOAD',
            'FILE_DOWNLOAD',
            'FILE_PREVIEW',
            'FILE_OPEN',
            'FILE_EDIT',
            'FILE_DELETE',
            'FILE_PERMANENT_DELETE',
            'FILE_RESTORE',
            'FILE_RENAME',
            'FILE_MOVE',
            'FILE_COPY',
            'FOLDER_CREATE',
            'FOLDER_DOWNLOAD',
            'FOLDER_DELETE',
            'FOLDER_PERMANENT_DELETE',
            'FOLDER_RESTORE',
            'FOLDER_RENAME',
        ],
    },
    { category: 'SHARE', types: ['SHARE_CREATE', 'SHARE_PERMISSION_CHANGE', 'SHARE_REMOVE'] },
    {
        category: 'USER_MANAGEMENT',
        types: [
            'USER_CREATE',
            'USER_DELETE',
            'USER_PERMANENT_DELETE',
            'USER_RESTORE',
            'USER_INFO_UPDATE',
            'USER_TWOFACTOR_RESET',
            'USER_PASSWORD_CHANGE',
            'USER_STATUS_CHANGE',
            'ADMIN_CREATE',
            'ADMIN_DELETE',
            'ADMIN_PERMANENT_DELETE',
            'ADMIN_RESTORE',
            'ADMIN_INFO_UPDATE',
            'ADMIN_PASSWORD_CHANGE',
            'ADMIN_STATUS_CHANGE',
        ],
    },
    { category: 'SETTING', types: ['SETTING_UPDATE', 'SETTING_ALLOWED_IP_CHANGE'] },
];

/** 카테고리 하위 이벤트 타입 목록 */
export const typesOfCategory = category =>
    EVENT_TYPE_GROUPS.find(group => group.category === category)?.types ?? [];

/**
 * 이벤트 조회 파라미터. 거버넌스 eventCategory는 단일 값이고 eventTypes와 AND라 같이 못 보낸다.
 * 카테고리 1개만 고른 경우에만 eventCategory로, 그 외에는 하위 타입을 펼쳐 eventTypes로 보낸다.
 */
const resolveEventParams = ({ eventCategories, eventTypes }) => {
    if (!eventCategories?.length) {
        return eventTypes?.length ? { eventTypes: eventTypes.join(',') } : {};
    }
    if (eventCategories.length === 1 && !eventTypes?.length) {
        return { eventCategory: eventCategories[0] };
    }

    const merged = new Set(eventTypes);
    eventCategories.forEach(category =>
        typesOfCategory(category).forEach(type => merged.add(type))
    );
    return { eventTypes: [...merged].join(',') };
};

/**
 * 화면 필터 상태 → 감사 로그 조회 API 공통 파라미터(페이징 제외).
 * 목록 조회와 CSV 내보내기가 "화면에 보이는 조건 그대로"를 공유해야 해서 여기 한 곳에 모은다.
 */
export const buildAuditLogApiParams = filters => {
    const { eventCategories, eventTypes, ...rest } = buildAuditLogQuery(filters, 1);
    return { ...rest, ...resolveEventParams(filters) };
};

/** 코드 → 문구. 키가 없는 코드는 코드를 그대로 보여준다(누락을 감추지 않기 위해). */
const translate = (keyMap, code, t) => {
    const key = keyMap[code];
    return key ? t(key) : (code ?? '');
};

export const CATEGORY_LABEL_KEY = {
    LOGIN: 'admin.auditLog.category.login',
    FILE_FOLDER: 'admin.auditLog.category.fileFolder',
    SHARE: 'admin.auditLog.category.share',
    USER_MANAGEMENT: 'admin.auditLog.category.userManagement',
    SETTING: 'admin.auditLog.category.setting',
};

export const categoryLabel = (category, t) => translate(CATEGORY_LABEL_KEY, category, t);

export const EVENT_TYPE_LABEL_KEY = {
    LOGIN: 'admin.auditLog.event.login',
    LOGOUT: 'admin.auditLog.event.logout',
    ACCOUNT_LOCKED_STATUS_CHANGE: 'admin.auditLog.event.accountLockedStatusChange',
    FILE_CREATE: 'admin.auditLog.event.fileCreate',
    FILE_UPLOAD: 'admin.auditLog.event.fileUpload',
    FILE_DOWNLOAD: 'admin.auditLog.event.fileDownload',
    FILE_PREVIEW: 'admin.auditLog.event.filePreview',
    FILE_OPEN: 'admin.auditLog.event.fileOpen',
    FILE_EDIT: 'admin.auditLog.event.fileEdit',
    FILE_DELETE: 'admin.auditLog.event.fileDelete',
    FILE_PERMANENT_DELETE: 'admin.auditLog.event.filePermanentDelete',
    FILE_RESTORE: 'admin.auditLog.event.fileRestore',
    FILE_RENAME: 'admin.auditLog.event.fileRename',
    FILE_MOVE: 'admin.auditLog.event.fileMove',
    FILE_COPY: 'admin.auditLog.event.fileCopy',
    FOLDER_CREATE: 'admin.auditLog.event.folderCreate',
    FOLDER_DOWNLOAD: 'admin.auditLog.event.folderDownload',
    FOLDER_DELETE: 'admin.auditLog.event.folderDelete',
    FOLDER_PERMANENT_DELETE: 'admin.auditLog.event.folderPermanentDelete',
    FOLDER_RESTORE: 'admin.auditLog.event.folderRestore',
    FOLDER_RENAME: 'admin.auditLog.event.folderRename',
    SHARE_CREATE: 'admin.auditLog.event.shareCreate',
    SHARE_PERMISSION_CHANGE: 'admin.auditLog.event.sharePermissionChange',
    SHARE_REMOVE: 'admin.auditLog.event.shareRemove',
    USER_CREATE: 'admin.auditLog.event.userCreate',
    USER_DELETE: 'admin.auditLog.event.userDelete',
    USER_PERMANENT_DELETE: 'admin.auditLog.event.userPermanentDelete',
    USER_RESTORE: 'admin.auditLog.event.userRestore',
    USER_INFO_UPDATE: 'admin.auditLog.event.userInfoUpdate',
    USER_TWOFACTOR_RESET: 'admin.auditLog.event.userTwofactorReset',
    USER_PASSWORD_CHANGE: 'admin.auditLog.event.userPasswordChange',
    USER_STATUS_CHANGE: 'admin.auditLog.event.userStatusChange',
    ADMIN_CREATE: 'admin.auditLog.event.adminCreate',
    ADMIN_DELETE: 'admin.auditLog.event.adminDelete',
    ADMIN_PERMANENT_DELETE: 'admin.auditLog.event.adminPermanentDelete',
    ADMIN_RESTORE: 'admin.auditLog.event.adminRestore',
    ADMIN_INFO_UPDATE: 'admin.auditLog.event.adminInfoUpdate',
    ADMIN_PASSWORD_CHANGE: 'admin.auditLog.event.adminPasswordChange',
    ADMIN_STATUS_CHANGE: 'admin.auditLog.event.adminStatusChange',
    SETTING_UPDATE: 'admin.auditLog.event.settingUpdate',
    SETTING_ALLOWED_IP_CHANGE: 'admin.auditLog.event.settingAllowedIpChange',
};

export const eventTypeLabel = (eventType, t) => translate(EVENT_TYPE_LABEL_KEY, eventType, t);

// change JSON key 라벨 (스펙 5-5). allowedIp는 스트링 리소스에 키가 없어 빠져 있다.
const CHANGE_KEY_LABEL_KEY = {
    name: 'admin.auditLog.field.name',
    phone: 'admin.auditLog.field.phone',
    countryCode: 'admin.auditLog.field.countryCode',
    storageQuota: 'admin.auditLog.field.storageQuota',
    permissions: 'admin.auditLog.field.permissions',
    scope: 'admin.auditLog.field.scope',
    loginMethod: 'admin.auditLog.field.loginMethod',
    globalLoginPolicy: 'admin.auditLog.field.globalLoginPolicy',
    globalSharePolicy: 'admin.auditLog.field.globalSharePolicy',
    externalShare: 'admin.auditLog.field.externalShare',
    added: 'admin.auditLog.field.added',
    removed: 'admin.auditLog.field.removed',
};

// 이름 변경/이동은 저장만 JSON이고 표시는 "{before} → {after}" 평문 (스펙 3-1)
const PLAIN_CHANGE_KEYS = new Set(['resourceName', 'path']);

// change 안의 enum 값 라벨 (스펙 5-4).
// 공유 범위는 백엔드 ShareType enum으로 오고 스트링 리소스 키는 스펙 이름이라 뜻이 같은 키로 잇는다.
const CHANGE_VALUE_LABEL_KEY = {
    ACTIVE: 'admin.auditLog.value.active',
    INACTIVE: 'admin.auditLog.value.inactive',
    LOCKED: 'admin.auditLog.value.locked',
    UNLOCKED: 'admin.auditLog.value.unlocked',
    ON: 'admin.auditLog.value.on',
    OFF: 'admin.auditLog.value.off',
    VIEW: 'admin.auditLog.value.view',
    DOWNLOAD: 'admin.auditLog.value.download',
    UPLOAD: 'admin.auditLog.value.upload',
    EDIT: 'admin.auditLog.value.edit',
    SHARE: 'admin.auditLog.value.share',
    SPECIFIC_USERS: 'admin.auditLog.value.restricted',
    ORG_LINK: 'admin.auditLog.value.orgInternal',
    PUBLIC_LINK: 'admin.auditLog.value.public',
    RESTRICTED: 'admin.auditLog.value.restricted',
    ORG_INTERNAL: 'admin.auditLog.value.orgInternal',
    PUBLIC: 'admin.auditLog.value.public',
    GLOBAL: 'admin.auditLog.value.global',
    TENANT: 'admin.auditLog.value.tenant',
    LOGIN_LOCAL: 'admin.auditLog.value.loginLocal',
    LOGIN_JWT: 'admin.auditLog.value.loginJwt',
    LOGIN_OIDC: 'admin.auditLog.value.loginOidc',
};

const CATEGORY_BADGE = {
    LOGIN: auditLogTokens.badge.waiting,
    FILE_FOLDER: auditLogTokens.badge.success,
    SHARE: auditLogTokens.badge.warning,
    USER_MANAGEMENT: auditLogTokens.badge.delay,
    SETTING: auditLogTokens.badge.default,
};

/** 이벤트 배지 색 — 카테고리 기준(성공/실패로는 색을 바꾸지 않는다) */
export const badgeColorOf = category => CATEGORY_BADGE[category] ?? auditLogTokens.badge.default;

/** 이벤트 배지 라벨 — 성공/실패는 결과 컬럼이 담당하므로 이벤트명만 표기한다 */
export const formatEventLabel = (item, t) => eventTypeLabel(item?.eventType, t);

// 경로 첫 세그먼트는 위치 루트 코드(MY/SHARE) — 다국어 라벨로 치환한다
const LOCATION_LABEL_KEY = { MY: 'common.label.myDrive', SHARE: 'common.label.shared' };
export const localizePath = (value, t) => {
    if (!value) return '';
    const segments = String(value).split('/');
    const key = LOCATION_LABEL_KEY[segments[0]];
    if (key) segments[0] = t(key);
    return segments.join(' / ');
};

/** 리소스 셀 첫째 줄 — 설정 이벤트는 objectName이 설정 키로 와서 라벨로 바꾼다. */
export const formatResourceName = (item, t) =>
    item?.objectType === 'SETTING'
        ? translate(CHANGE_KEY_LABEL_KEY, item.objectName, t)
        : item?.objectName;

/** 리소스 셀 둘째 줄 — 대상이 사용자면 이메일, 파일·폴더면 경로. */
export const formatResourceSub = (item, t) =>
    item?.objectType === 'USER' ? (item.objectEmail ?? '') : localizePath(item?.objectPath, t);

/** 처리 결과 — FAIL만 실패로 보고 나머지(SUCCESS/미기록)는 성공으로 표기한다 */
export const formatOutcome = (item, t) =>
    item?.outcomeStatus === 'FAIL'
        ? t('admin.auditLog.result.failed')
        : t('admin.auditLog.result.success');

/** 일시 — 2026.06.29 · 09:01 (백엔드가 KST 문자열로 내려줌) */
export const formatOccurredAt = value => {
    if (!value) return '';
    // 공백 구분은 브라우저마다 파싱이 갈려 ISO 로컬 표기로 바꿔 넘긴다(타임존 변환 없음)
    const d = dayjs(String(value).replace(' ', 'T'));
    return d.isValid() ? d.format('YYYY.MM.DD · HH:mm') : String(value);
};

const labelValue = (value, t) => {
    if (value === null || value === undefined || value === '') return '';
    if (Array.isArray(value)) return value.map(item => labelValue(item, t)).join(', ');
    if (typeof value === 'object') return JSON.stringify(value);
    return translate(CHANGE_VALUE_LABEL_KEY, value, t);
};

// 바이트로 기록되는 change 키
const BYTE_CHANGE_KEYS = new Set(['storageQuota']);

/** 바이트 값을 표시 단위로. 음수는 무제한 약속값이다. */
const byteText = value => {
    if (value === null || value === undefined || value === '') return '';
    const bytes = Number(value);
    if (Number.isNaN(bytes)) return String(value);
    return bytes < 0 ? '무제한' : convertBytesToReadableSize(bytes);
};

// 공유 권한은 불리언 묶음으로 와서 표시 전에 permissions 배열로 접는다
const SHARE_PERMISSION_FLAGS = [
    ['canView', 'VIEW'],
    ['canDownload', 'DOWNLOAD'],
    ['canUpload', 'UPLOAD'],
    ['canEdit', 'EDIT'],
    ['canShare', 'SHARE'],
];

const isPlainObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

const keyLabel = (key, t) => translate(CHANGE_KEY_LABEL_KEY, key, t);

/**
 * change의 한쪽(before/after)을 표시 모양으로 정리한다 — 권한 불리언은 permissions로 접고
 * shareType은 scope로 옮긴다(표시 순서: 범위 → 권한). 해당 키가 없으면 원본 그대로.
 */
const normalizeChangeSide = value => {
    if (!isPlainObject(value)) return value;

    const rest = { ...value };
    const hasFlags = SHARE_PERMISSION_FLAGS.some(([flag]) => flag in rest);
    const hasShareType = 'shareType' in rest;
    if (!hasFlags && !hasShareType && !('scope' in rest)) return value;

    const permissions = [];
    SHARE_PERMISSION_FLAGS.forEach(([flag, permission]) => {
        if (!(flag in rest)) return;
        if (rest[flag]) permissions.push(permission);
        delete rest[flag];
    });
    // shareType(구 계약)과 scope(신 계약) 둘 다 공유 범위를 뜻한다
    const scope = hasShareType ? rest.shareType : rest.scope;
    delete rest.shareType;
    delete rest.scope;

    return {
        ...(scope !== undefined ? { scope } : {}),
        ...(hasFlags ? { permissions } : {}),
        ...rest,
    };
};

/** "{before} → {after}" — 한쪽만 있거나 전후가 같으면(스냅샷에 안 바뀐 필드가 섞인 경우) 값 하나만 */
const arrowText = (before, after) => {
    if (before && after) return before === after ? after : `${before} → ${after}`;
    return after || before;
};

/** change(before/after)를 필드별 [{ key, text }]로. 단일값이면 key=null 한 건. */
const changeEntries = (change, t) => {
    const before = normalizeChangeSide(change?.before);
    const after = normalizeChangeSide(change?.after);
    if (before === undefined && after === undefined) return [];

    if (!isPlainObject(before) && !isPlainObject(after)) {
        const text = arrowText(labelValue(before, t), labelValue(after, t));
        return text ? [{ key: null, text }] : [];
    }

    const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])];
    return keys
        .map(key => {
            const toText = v => {
                // 경로 값은 위치 루트(MY/SHARE)를 다국어 라벨로 치환
                if (key === 'path') return localizePath(v, t);
                if (BYTE_CHANGE_KEYS.has(key)) return byteText(v);
                return labelValue(v, t);
            };
            return { key, text: arrowText(toText(before?.[key]), toText(after?.[key])) };
        })
        .filter(entry => entry.text);
};

/** 단독 표기 — "[필드명] 값 · [필드명] 값". 이름 변경/이동(resourceName·path)은 라벨 없이 평문 */
const formatChangeBlock = (entries, t) =>
    entries
        .map(({ key, text }) =>
            key === null || PLAIN_CHANGE_KEYS.has(key) ? text : `[${keyLabel(key, t)}] ${text}`
        )
        .join(' · ');

/** 공유 대상자 뒤 괄호 표기 — "필드명: 값" (내부·외부 테넌트 동일) */
const formatChangeInline = (entries, t) =>
    entries.map(({ key, text }) => (key ? `${keyLabel(key, t)}: ${text}` : text)).join(', ');

/**
 * targets[] 한 항목은 거버넌스 Target 구조다(SDK 0.7.0) —
 * {
 *   type: USER | TENANT | ORGANIZATION,
 *   selection: ACCOUNTS | ALL,
 *   entity: { id, name },              // 외부 테넌트·조직 공유일 때
 *   accounts: [{ id, name, email, permission: { before, after } }],   // selection=ACCOUNTS
 *   permission: { before, after },     // selection=ALL (링크·조직 공유)
 *   shareScope: { before, after },     // 공유 범위
 * }
 * 권한이 change가 아니라 대상 안에 있어 대상별로 조립한다.
 */
const entityName = target => target?.entity?.name ?? target?.entityName ?? '';

/** "이름(이메일)" — 한쪽만 있으면 있는 것만 */
const accountText = (account, t) => {
    if (!isPlainObject(account)) return labelValue(account, t);
    const name = account.name ?? '';
    const email = account.email ?? '';
    return name && email ? `${name}(${email})` : name || email || '';
};

/** 공유 범위(shareScope)와 권한(permission)을 한 change 모양 {before, after}로 합친다 */
const shareChangeOf = (shareScope, permission) => {
    const side = key => {
        const scope = shareScope?.[key];
        const perm = permission?.[key];
        if (scope === undefined && perm === undefined) return undefined;
        return {
            ...(scope !== undefined ? { scope } : {}),
            ...(isPlainObject(perm) ? perm : {}),
        };
    };
    return { before: side('before'), after: side('after') };
};

/** 대상 하나를 표시 문자열 목록으로. 대상자가 있으면 대상자마다 한 줄이 된다. */
const targetTexts = (target, t) => {
    if (!isPlainObject(target)) {
        const text = labelValue(target, t);
        return text ? [text] : [];
    }

    const tenant = entityName(target);
    const accounts = Array.isArray(target.accounts) ? target.accounts : [];

    // 대상자 없는 공유(링크·조직 전체) — 범위·권한만 "[필드명] 값" 형태로
    if (!accounts.length) {
        const entries = changeEntries(shareChangeOf(target.shareScope, target.permission), t);
        const block = formatChangeBlock(entries, t);
        return block ? [block] : tenant ? [tenant] : [];
    }

    return accounts
        .map(account => {
            const base = accountText(account, t);
            if (!base) return '';
            const withTenant = tenant ? `${base} · ${tenant}` : base;
            const entries = changeEntries(shareChangeOf(target.shareScope, account.permission), t);
            const inline = formatChangeInline(entries, t);
            return inline ? `${withTenant} (${inline})` : withTenant;
        })
        .filter(Boolean);
};

/**
 * 타겟 셀 (스펙 4-2 조합 규칙 + TFDRIVE-1728 표기 가이드).
 * - 대상 없음: change만 "[필드명] 값 · [필드명] 값"
 * - 대상 있음: 대상마다 조립한 문자열을 '·'로 잇는다(권한은 각 대상 안에 있다).
 */
export const formatTarget = (item, t) => {
    const targets = Array.isArray(item?.targets) ? item.targets : [];
    const targetText = targets.flatMap(target => targetTexts(target, t)).join(' · ');
    if (targetText) return targetText;

    return formatChangeBlock(changeEntries(item?.change, t), t);
};
