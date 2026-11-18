import dayjs from 'dayjs';

export const DateFilter = [
    {
        name: 'data.lastModified',
        value: 'update',
    },
    {
        name: 'data.lastOpened',
        value: 'open',
    },
];

export const TypeFilter = [
    {
        name: 'fileTypeFilter.document',
        value: 'document',
        src: '/common/icon-file-ic-file-word-24.svg',
        type: ['doc', 'docx', 'pdf', 'hwp', 'odt', 'rtf'],
    },
    {
        name: 'fileTypeFilter.spreadsheet',
        value: 'spreadsheet',
        src: '/common/icon-file-ic-file-spreadsheet-24.svg',
    },
    {
        name: 'fileTypeFilter.presentation',
        value: 'presentation',
        src: '/common/icon-file-ic-file-presentation-24.svg',
    },
    {
        name: 'fileTypeFilter.image',
        value: 'image',
        src: '/common/icon-file-ic-file-img-24.svg',
    },
    {
        name: 'fileTypeFilter.pdf',
        value: 'pdf',
        src: '/common/icon-file-ic-file-pdf-24.svg',
    },
    {
        name: 'fileTypeFilter.note',
        value: 'note',
        src: '/common/icon-file-ic-file-intellect-24.svg',
    },
];

export const RangeDateFilter = [
    {
        name: 'common.dateFilter.today',
        value: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
    },
    {
        name: 'common.dateFilter.last7Days',
        value: [dayjs().subtract(6, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
    },
    {
        name: 'common.dateFilter.last30Days',
        value: [dayjs().subtract(29, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
    },
    {
        name: 'common.dateFilter.thisYear',
        value: [dayjs().startOf('year').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
    },
];

export const WithinDateFilter = [
    {
        name: 'dateFilter.scheduledToday',
        value: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
    },
    {
        name: 'dateFilter.scheduledTomorrow',
        value: [
            dayjs().add(1, 'day').format('YYYY-MM-DD'),
            dayjs().add(1, 'day').format('YYYY-MM-DD'),
        ],
    },
    {
        name: 'dateFilter.scheduledWithin7Days',
        value: [dayjs().format('YYYY-MM-DD'), dayjs().add(6, 'day').format('YYYY-MM-DD')],
    },
    {
        name: 'dateFilter.scheduledWithin30Days',
        value: [dayjs().format('YYYY-MM-DD'), dayjs().add(29, 'day').format('YYYY-MM-DD')],
    },
];

/** 계정 목록 역할 필터 — 서버 rt(보유 기준) */
export const RoleFilter = [
    {
        name: 'common.label.user',
        value: 'USER',
    },
    {
        name: 'common.label.admin',
        value: 'ADMIN',
    },
];

export const StatusFilter = [
    {
        name: 'common.action.activate',
        value: 'active',
    },
    {
        name: 'common.action.deactivate',
        value: 'inactive',
    },
];
