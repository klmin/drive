import { getItemExt, getOfficeFormat } from '@/constants/FILES';
import api from '@/utils/api';
import { resolveLocale } from '@/utils/localeUtils';

/** TFO 앱 계열 × 열기 방식 → 오피스 실행 모드 */
const OFFICE_MODES = {
    // 한워드
    word: { edit: 'WRITE_EDITOR', view: 'WRITE_VIEWER' },
    // 한셀
    sheet: { edit: 'CALC_EDITOR', view: 'CALC_VIEWER' },
    // 한쇼
    slide: { edit: 'SHOW_EDITOR', view: 'SHOW_VIEWER' },
    // PDF (편집 불가)
    pdf: { edit: 'PDF_VIEWER', view: 'PDF_VIEWER' },
};

const checkMode = (format, type) => OFFICE_MODES[format]?.[type];

const requestOfficeUrl = async (item, user, type, authMode) => {
    if (!item || !user) return;
    const app = checkMode(getOfficeFormat(getItemExt(item)), type);
    if (!app) return;
    const configuration = await api.Get('config/office');
    if (configuration?.data?.data?.officeConnectionType !== 'onPremise') return;
    const response = await api.Post('office/client-sessions', {
        ownerUserSeq: item.sharedByUserSeq || item.userSeq || user.userSeq,
        resourceSeq: item.resourceSeq,
        app,
        authMode,
        lang: resolveLocale(user.userLanguage).replace('-', '_'),
    });
    return response?.data?.url;
};

export const getOfficeViewUrl = (item, user, authMode) =>
    requestOfficeUrl(item, user, 'view', authMode);
export const getOfficeEditUrl = (item, user, isCreate = false, authMode) =>
    requestOfficeUrl(item, user, 'edit', authMode);

// createDocument
export const createDocument = async (tt, user, pfs = '', ss = '') => {
    if (!tt || !user) {
        console.warn('No tt or user available for createOfficeDocumentAndGetEditUrl');
        return;
    }

    try {
        const res = await api.Post(
            `documents`,
            {
                pfs,
                tt,
                ss,
            },
            {
                headers: {
                    'Accept-Language': resolveLocale(user.userLanguage),
                },
            }
        );
        return res;
    } catch (error) {
        console.error('createOfficeDocumentAndGetEditUrl', error);
    }
};
