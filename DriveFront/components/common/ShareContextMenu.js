import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getItemExt, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl } from '@/apis/webOfficeApi';
import { getResourceDetail } from '@/apis/resourceApi';
import { registerRecent } from '@/apis/recentApi';
import checkDevice from '@/utils/etc';
import { hasLockedFile } from '@/utils/resourceUtils';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import useClientConfig from '@/hooks/useClientConfig';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import useOfficeViewer from '@/hooks/useOfficeViewer';
import { useUser } from '@/context/UserContext';
import { formatShortcut } from '@/utils/platform';

const menuStyles = {
    position: 'fixed',
    zIndex: 1202,
    minWidth: 220,
    borderRadius: '6px',
    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
    border: `1px solid ${palette.control_border_normal}`,
    backgroundColor: palette.btn_white_bg_normal,
    padding: '8px 0',
    listStyle: 'none',
    margin: 0,
};

function CustomMenuItem({ text, keyText, ...props }) {
    return (
        <MenuItem {...props} sx={{ width: '100%', px: 3 }}>
            <Stack
                sx={{ width: '100%' }}
                direction="row"
                justifyContent="space-between"
                alignItems="center"
            >
                <Typography variant="body5" color="text_80">
                    {text}
                </Typography>
                <Typography variant="body9" color="text_60">
                    {keyText}
                </Typography>
            </Stack>
        </MenuItem>
    );
}

export default function ShareContextMenu({ menuState, onClose, checkItems, setCheckItems }) {
    const menuRef = useRef(null);
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();
    const { isOfficeViewerTarget, openOfficeViewer } = useOfficeViewer();
    const user = useUser();
    const device = checkDevice();

    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });

    const { handleDownload } = useDriveDownload({ onClose, t });

    const items = useMemo(
        () => (checkItems?.length > 1 ? checkItems : [menuState.selectedRow]),
        [menuState.selectedRow, checkItems]
    );

    const item = items?.[0];

    const ss = router.query.ss ?? '';

    const isFolder = item?.resourceType === 'FOLDER';
    const isNotOffice = !officeFeature.canEdit(getItemExt(item));
    const isNotIntellect = item ? !isIntellectFile(getItemExt(item)) : true;
    const isIntellect = item ? isIntellectFile(getItemExt(item)) : false;
    const isLocked = item ? hasLockedFile(items) : false;

    const handlePreview = () => {
        if (isFolder) {
            router.push({ pathname: '/s/folder', query: { pfs: item.resourceSeq, ss } });
        } else if (isOfficeViewerTarget(item)) {
            // http TFO 오피스는 다이얼로그 대신 뷰어 새 창
            openOfficeViewer(item);
        } else {
            openDialog('preview', {
                previewItem: item,
                user: user,
            });
        }
        onClose();
    };

    const handleEdit = async () => {
        const popup = openPendingPopup();
        const previewResource = await getResourceDetail(item?.resourceSeq, {
            ss: item?.sharedByUserSeq || item?.userSeq,
        });
        if (!previewResource) {
            closePendingPopup(popup);
            return;
        }

        if (isIntellect && previewResource.isLocked) {
            closePendingPopup(popup);
            showSnackbar(t('lock.locked'), 'error');
            onClose();
            return;
        }

        const editUrl = isIntellect
            ? await getIntellectUrl(previewResource, user)
            : await getOfficeEditUrl(previewResource, user);

        if (!editUrl) {
            closePendingPopup(popup);
            showSnackbar(t('user.filePreview.toast.openDocumentFail'), 'error');
            return;
        }

        openDocumentWindow(editUrl, popup);

        const registered = await registerRecent(
            previewResource.resourceSeq,
            previewResource.sharedByUserSeq || previewResource.userSeq
        );
        if (!registered) {
            showSnackbar(t('recent.register.fail'), 'error');
        }
        showSnackbar(t('user.filePreview.toast.openDocumentFail'), 'success');

        onClose();
    };

    const canEdit =
        !isFolder &&
        item?.canEdit &&
        (!isNotOffice || !isNotIntellect) &&
        !device.isPhone &&
        !(isIntellect && isLocked) &&
        !(isIntellect && !intellectEnabled);

    // 메뉴 위치 보정
    useLayoutEffect(() => {
        if (!menuRef.current) return;

        const { offsetHeight, offsetWidth } = menuRef.current;
        const { innerHeight, innerWidth } = window;

        let top = menuState.top;
        let left = menuState.left;

        if (window.event?.target?.id === 'dots') {
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 12;
        } else if (window.event?.target?.id === 'dots-button') {
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 6;
        }

        if (top + offsetHeight > innerHeight) top = innerHeight - offsetHeight - 10;
        if (left + offsetWidth > innerWidth) left = innerWidth - offsetWidth - 100;

        setPosition({ top, left });
    }, [menuState.top, menuState.left, menuState.visible]);

    useEffect(() => {
        const handleClickOutside = e => {
            if (menuRef.current && !menuRef.current.contains(e.target)) onClose();
        };
        if (menuState.visible) {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('touchstart', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('touchstart', handleClickOutside);
        };
    }, [menuState.visible, onClose]);

    if (!menuState.visible || !item) return null;

    return (
        <ul ref={menuRef} style={{ ...menuStyles, top: position.top, left: position.left }}>
            {items.length === 1 && (
                <CustomMenuItem
                    text={t('common.action.previewOpen')}
                    keyText={`${formatShortcut('Enter')}`}
                    onClick={handlePreview}
                    disabled={!item?.canView}
                />
            )}
            {canEdit && <CustomMenuItem text={t('common.action.edit')} onClick={handleEdit} />}
            <CustomMenuItem
                text={t('common.action.download')}
                onClick={() => handleDownload(items)}
                disabled={!item?.canDownload}
            />
        </ul>
    );
}
