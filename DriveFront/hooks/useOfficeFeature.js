import { useMemo } from 'react';
import useClientConfig from '@/hooks/useClientConfig';
import { isOfficeEditable, isOfficePreviewable } from '@/constants/FILES';

/** 오피스 새 문서 메뉴 — New+·빈 공간 우클릭·홈 빠른 메뉴 공용. */
export const OFFICE_CREATE_MENUS = [
    { type: 'word', icon: 'doc', label: 'uploadDialog.newDocs' },
    { type: 'excel', icon: 'xlsx', label: 'uploadDialog.newExcel' },
    { type: 'ppt', icon: 'ppt', label: 'uploadDialog.newPpt' },
];

export const OFFICE_CREATE_TYPES = OFFICE_CREATE_MENUS.map(menu => menu.type);

/** 오피스 on/off 에 따른 기능 노출 판정. 정책이 바뀌면 여기만 고친다. */
export const getOfficeFeature = (officeEnabled, officeHttps = null) => {
    const enabled = officeEnabled === true;
    return {
        /** on/off 확인 완료 여부(/config 응답 전엔 false) */
        ready: officeEnabled !== null && officeEnabled !== undefined,
        /** 새 오피스 문서 생성 메뉴 노출 */
        canCreate: enabled,
        /** 노출할 새 오피스 문서 메뉴 목록 */
        createMenus: enabled ? OFFICE_CREATE_MENUS : [],
        /** 오피스 편집 메뉴 노출 */
        canEdit: ext => enabled && isOfficeEditable(ext),
        /** 미리보기를 오피스 뷰어로 열지 여부(false 면 일반 파일처럼 표시) */
        canPreview: ext => enabled && isOfficePreviewable(ext),
        /** 미리보기 방식(iframe·새 탭) 확인 완료 여부 */
        previewReady:
            officeEnabled !== null &&
            officeEnabled !== undefined &&
            (!enabled || (officeHttps !== null && officeHttps !== undefined)),
        /** 미리보기를 iframe 대신 새 탭(에디터·뷰어)으로 열지 여부 — TFO 주소가 http 일 때 */
        previewInNewTab: ext => enabled && officeHttps === false && isOfficePreviewable(ext),
    };
};

/** 현재 테넌트의 오피스 기능 노출 판정. */
export default function useOfficeFeature() {
    const { officeEnabled, officeHttps } = useClientConfig();
    return useMemo(
        () => getOfficeFeature(officeEnabled, officeHttps),
        [officeEnabled, officeHttps]
    );
}
