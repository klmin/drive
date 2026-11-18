import { useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import { getResourceDetail } from '@/apis/resourceApi';
import { getOfficeEditUrl, getOfficeViewUrl } from '@/apis/webOfficeApi';
import { registerRecent } from '@/apis/recentApi';
import { getItemExt } from '@/constants/FILES';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';

/** http TFO 오피스 열기 — 다이얼로그 대신 새 창으로 연다. 응답 후 창을 여는 방식은 편집 메뉴(handleEdit)와 동일 */
export default function useOfficeViewer() {
    const t = useTranslations();
    const user = useUser();
    const officeFeature = useOfficeFeature();
    const { showSnackbar } = useSnackbarContext();

    /** 뷰어 새 창으로 열 파일인지 — 오피스이고 TFO 주소가 http */
    const isOfficeViewerTarget = useCallback(
        item => item?.resourceType !== 'FOLDER' && officeFeature.previewInNewTab(getItemExt(item)),
        [officeFeature]
    );

    /**
     * 상세 조회 → 문서 주소 발급 → 새 창. resource 를 넘기면 상세 조회 생략, deleted 는 휴지통 파일.
     * preferEdit 이면 편집 권한이 있을 때 에디터, 없으면 뷰어로 연다.
     */
    const openOfficeViewer = useCallback(
        async (item, { resource, deleted = false, preferEdit = false } = {}) => {
            const popup = openPendingPopup();
            const detail =
                resource ??
                (await getResourceDetail(item?.resourceSeq, {
                    ss: item?.sharedByUserSeq || item?.userSeq,
                    d: deleted,
                }));
            if (!detail) {
                closePendingPopup(popup);
                showSnackbar(t('preview.fail'), 'error');
                return;
            }

            const openEditor =
                preferEdit &&
                !deleted &&
                detail.canEdit &&
                officeFeature.canEdit(getItemExt(detail));
            const documentUrl = openEditor
                ? await getOfficeEditUrl(detail, user)
                : await getOfficeViewUrl(detail, user);
            if (!documentUrl) {
                closePendingPopup(popup);
                showSnackbar(t('user.filePreview.toast.openDocumentFail'), 'error');
                return;
            }
            openDocumentWindow(documentUrl, popup);

            const registered = await registerRecent(
                detail.resourceSeq,
                detail.sharedByUserSeq || detail.userSeq
            );
            if (!registered) {
                showSnackbar(t('recent.register.fail'), 'error');
            }
        },
        [t, user, officeFeature, showSnackbar]
    );

    return { isOfficeViewerTarget, openOfficeViewer };
}
