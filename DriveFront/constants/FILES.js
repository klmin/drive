const FILES = [
    {
        name: 'doc',
        label: '문서',
        src: '/common/icon-file-ic-file-word-24.svg',
        type: ['doc', 'docx', 'hwp', 'hwpx', 'odt', 'pages'],
    },
    {
        name: 'xlsx',
        label: '엑셀',
        src: '/common/icon-file-ic-file-spreadsheet-24.svg',
        type: ['xls', 'xlsx', 'csv', 'tsv', 'ods', 'numbers'],
    },
    {
        name: 'ppt',
        label: '프레젠테이션',
        src: '/common/icon-file-ic-file-presentation-24.svg',
        type: ['ppt', 'pptx', 'odp', 'key'],
    },
    {
        name: 'image',
        label: '이미지',
        src: '/common/icon-file-ic-file-img-24.svg',
        type: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'tiff', 'tif', 'heic'],
    },
    {
        name: 'pdf',
        label: 'PDF',
        src: '/common/icon-file-ic-file-pdf-24.svg',
        type: ['pdf'],
    },
    {
        name: 'txt',
        label: '텍스트',
        src: '/common/icon-file-ic-file-txt-24.svg',
        type: ['txt', 'rtf', 'md', 'log', 'json', 'xml', 'yaml', 'yml'],
    },
    {
        name: 'etc',
        label: '기타',
        src: '/common/icon-file-ic-file-etc-24.svg',
        type: [],
    },
    {
        name: 'zip',
        label: '압축',
        src: '/common/icon-file-ic-file-zip-24.svg',
        type: ['zip', 'rar', '7z', 'tar', 'gz', 'tgz'],
    },
    {
        name: 'folder',
        label: '폴더',
        src: '/common/icon-file-ic-folder-24.svg',
        type: ['folder'],
    },
    {
        name: 'intellect',
        label: '인텔릭트 페이지',
        src: '/common/icon-file-ic-file-intellect-24.svg',
        type: ['tfi'],
    },
];

export const getFile = name => {
    return FILES.find(f => f.name === name);
};

/** 비교용 확장자 정규화(소문자). 빈 값은 null */
const normalizeExt = ext => (typeof ext === 'string' && ext ? ext.toLowerCase() : null);

/** 확장자(점 제외)로 아이콘 분류 조회. 폴더는 type 으로 판정 */
export const getFileByFileType = (ext, type) => {
    if (type === 'FOLDER' || type === 'folder') {
        return FILES[8];
    }

    const extension = normalizeExt(ext);
    const file = extension
        ? FILES.find(f => Array.isArray(f.type) && f.type.includes(extension))
        : null;

    return file || FILES[6];
};

/** 이름의 마지막 점 뒤를 확장자로 추출(소문자). 없으면 null */
export const getFileExtension = fileName => {
    if (!fileName || typeof fileName !== 'string') return null;
    const match = fileName.match(/\.([0-9a-z]+)(?:[\?#]|$)/i);
    return match ? match[1].toLowerCase() : null;
};

/** 항목의 확장자(소문자, 점 제외). ext 필드가 없을 때만 이름에서 추출 */
export const getItemExt = (item, fileName = item?.resourceName) => {
    if (typeof item?.ext === 'string') return item.ext.toLowerCase();
    return getFileExtension(fileName);
};

export const getFileNameOnly = fileName => {
    const parts = fileName.split('.');
    return parts.length > 1 ? parts.slice(0, -1).join('.') : fileName;
};

/** TFO 지원 확장자 → 앱 계열. 아이콘 분류(FILES)와 무관하게 별도 관리한다. */
const TFO_EXTENSION_APPS = new Map([
    // 한워드
    ['doc', 'word'],
    ['docx', 'word'],
    ['odt', 'word'],
    // 한셀
    ['xls', 'sheet'],
    ['xlsx', 'sheet'],
    ['cell', 'sheet'],
    ['ods', 'sheet'],
    ['csv', 'sheet'],
    // 한쇼
    ['ppt', 'slide'],
    ['pptx', 'slide'],
    ['show', 'slide'],
    ['odp', 'slide'],
    // PDF
    ['pdf', 'pdf'],
]);

/** 열람만 가능하고 편집은 불가한 확장자 */
const TFO_VIEW_ONLY_EXTENSIONS = new Set(['pdf']);

const INTELLECT_TYPE_NAMES = new Set(['intellect']);

/** TFO 앱 계열 반환. 미지원 확장자는 null. */
export const getOfficeFormat = ext => {
    return TFO_EXTENSION_APPS.get(normalizeExt(ext)) ?? null;
};

export const isOfficeEditable = ext => {
    const extension = normalizeExt(ext);
    return TFO_EXTENSION_APPS.has(extension) && !TFO_VIEW_ONLY_EXTENSIONS.has(extension);
};

export const isOfficePreviewable = ext => {
    return getOfficeFormat(ext) !== null;
};

export const isIntellectFile = ext => {
    return INTELLECT_TYPE_NAMES.has(getFileByFileType(ext)?.name);
};

export default FILES;
