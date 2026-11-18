import { useUser } from '@/context/UserContext';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getResourceDetail } from '@/apis/resourceApi';
import { useRouter } from '@/hooks/useRouter';
import useOfficeViewer from '@/hooks/useOfficeViewer';

export function useDrivePreview({
    onClose = () => {},
    t,
    confirmRestore = true,
    handleRestore = () => {},
}) {
    const user = useUser();
    const router = useRouter();
    const { showSnackbar } = useSnackbarContext();
    const { openDialog } = useDialog();
    const { isOfficeViewerTarget, openOfficeViewer } = useOfficeViewer();

    // preview 다이얼로그 열기
    const openPreviewDialog = (item, preferEdit = false) => {
        openDialog('preview', { previewItem: item, user, preferEdit });
        onClose();
    };

    const handleDeletedPreview = (item, isFile) => {
        if (!isFile) {
            openDialog('alert', {
                title: t('restoreBeforeViewDialog.folderText1'),
                description: t('restoreBeforeViewDialog.folderText2'),
                buttonName: t('common.action.restore'),
                onClick: () => handleRestore(item),
            });
        } else if (isOfficeViewerTarget(item)) {
            openOfficeViewer(item, { deleted: true });
        } else {
            openPreviewDialog(item);
        }

        onClose();
    };

    /** 폴더 이동 처리 (default / upload 모드) */
    const navigateToFolder = (item, mode = 'default') => {
        const ss = item.sharedByUserSeq || item.userSeq || '';
        const isOrgLink = router.query.st === 'ORG_LINK';

        let root;

        if (mode === 'upload') {
            // 업로드 다이얼로그 판단 기준
            root = ss === user?.userSeq ? '/drive' : '/shared';
        } else {
            root =
                router.pathname === '/shared' || item.resourceLocation === 'SHARE'
                    ? '/shared'
                    : '/drive';
        }

        let path = `${root}?pfs=${item.resourceSeq}&ss=${ss}`;
        if (isOrgLink) path += '&st=ORG_LINK';
        router.push(path);
        onClose();
    };

    // ------------------------------------------------------------------------------------
    // 일반 프리뷰 — preferEdit(이름 클릭·더블클릭)면 편집 권한 있는 http 오피스는 에디터로
    const handlePreview = async (item, { preferEdit = false } = {}) => {
        const isFile = item.resourceType === 'FILE';
        const isDeleted = !!item?.deleteDate;

        // 휴지통 리소스인 경우
        if (confirmRestore && isDeleted) {
            return handleDeletedPreview(item, isFile);
        }

        // 폴더 열기
        if (!isFile) {
            return navigateToFolder(item, 'default');
        }

        // 파일 프리뷰 — http TFO 오피스는 다이얼로그 대신 새 창
        if (isOfficeViewerTarget(item)) {
            onClose();
            return openOfficeViewer(item, { preferEdit });
        }
        openPreviewDialog(item, preferEdit);
        onClose();
    };

    // 업로드 다이얼로그 프리뷰
    const handleUploadDialogPreview = async item => {
        try {
            const result = await getResourceDetail(item?.pfs, { ss: item?.query?.ss });

            if (!result) {
                showSnackbar(t('preview.fail'), 'error');
                return;
            }
            const isFile = result.resourceType === 'FILE';

            if (!isFile) {
                return navigateToFolder(result, 'upload');
            }

            if (isOfficeViewerTarget(result)) {
                return openOfficeViewer(result, { resource: result, preferEdit: true });
            }
            return openPreviewDialog(result, true);
        } catch (e) {
            console.error(e);
            showSnackbar(t('preview.fail'), 'error');
        }
    };

    return { handlePreview, handleUploadDialogPreview };
}
