import React, { useEffect, useRef, useState, useLayoutEffect } from 'react';
import {
    Divider,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { getResourceDetail } from '@/apis/resourceApi';
import { useUser } from '@/context/UserContext';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getFile } from '@/constants/FILES';
import Image from 'next/image';
import { createDocument, getOfficeEditUrl } from '@/apis/webOfficeApi';
import { registerRecent } from '@/apis/recentApi';
import checkDevice from '@/utils/etc';
import typography from '@/theme/typography';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import useClientConfig from '@/hooks/useClientConfig';
import useOfficeFeature from '@/hooks/useOfficeFeature';

const menuStyles = {
    position: 'fixed',
    zIndex: 1300,
    minWidth: 179,
    borderRadius: '6px',
    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
    border: `1px solid ${palette.control_border_normal}`,
    backgroundColor: palette.btn_white_bg_normal,
    padding: '8px 0',
    listStyle: 'none',
    margin: 0,
};

const StyledDriveContextMenu = ({ menuState, onClose, reload }) => {
    const menuRef = useRef(null);
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();

    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });
    const fileInputRef = useRef(null);
    const folderInputRef = useRef(null);

    const device = checkDevice();
    const isPhone = device?.isPhone;
    const isTablet = device?.isTablet;

    const router = useRouter();
    const user = useUser();

    const createNewDocument = async type => {
        const isNote = type === 'note';
        const popup = openPendingPopup();

        try {
            const pfs = router.query.pfs || '';
            const ss = router.query.ss || '';
            const root = router.asPath.replace(/^\//, '').split(/[/?#]/)[0];

            const res = await createDocument(type, user, pfs, ss ? ss : user?.userSeq);
            if (res?.code !== 201) {
                closePendingPopup(popup);
                showSnackbar(t('newOfficeCreate.fail'), 'error');
                return;
            }

            const documentResourceSeq = res.data.resourceSeq;
            const documentSs = res.data.sharedByUserSeq || res.data.userSeq;
            const newDocument = await getResourceDetail(documentResourceSeq, { ss: documentSs });

            let editUrl = isNote
                ? await getIntellectUrl(newDocument, user)
                : await getOfficeEditUrl(newDocument, user);
            if (!editUrl) throw new Error('Edit URL not generated');

            openDocumentWindow(editUrl, popup);

            const registered = await registerRecent(documentResourceSeq, documentSs);
            if (!registered) {
                showSnackbar(t('recent.register.fail'), 'error');
            }
            const query = pfs ? `?pfs=${pfs}&ss=${ss}` : ``;
            router.push(`/${root}${query}`);
            window.postMessage('drive_reload', '*');
            window.postMessage('quota_reload', '*');
            showSnackbar(
                t(isNote ? 'newIntellectCreate.success' : 'newOfficeCreate.success'),
                'success'
            );
        } catch (error) {
            console.log(error);
            closePendingPopup(popup);
            showSnackbar(t(isNote ? 'common.toast.fail' : 'newOfficeCreate.fail'), 'error');
            return;
        }
    };

    // 메뉴 위치 보정
    useLayoutEffect(() => {
        if (!menuRef.current) return;

        const { offsetHeight, offsetWidth } = menuRef.current;
        const { innerHeight, innerWidth } = window;

        let top = menuState.top;
        let left = menuState.left;

        if (top + offsetHeight > innerHeight) {
            top = innerHeight - offsetHeight - 10; // 아래 벗어남 → 위로 띄움
        }

        if (left + offsetWidth > innerWidth) {
            left = innerWidth - offsetWidth - 10; // 오른쪽 벗어남 → 왼쪽으로
        }

        setPosition({ top, left });
    }, [menuState.top, menuState.left, menuState.visible]);

    useEffect(() => {
        const handleClickOutside = event => {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                onClose();
            }
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

    if (!menuState.visible) {
        return null;
    }

    return (
        <ul
            ref={menuRef}
            style={{
                ...menuStyles,
                top: position.top,
                left: position.left,
            }}
        >
            <ListItemButton
                sx={{
                    px: 6,
                    gap: 2,
                }}
                onClick={() => {
                    openDialog('folderCreate', {
                        pfs: router.query.pfs || '',
                        ss: router.query.ss || '',
                    });
                }}
            >
                <ListItemIcon sx={{ minWidth: 28 }}>
                    <Image src={getFile('folder').src} alt={'folder'} width={24} height={24} />
                </ListItemIcon>
                <ListItemText
                    primary={t('uploadDialog.newFolder')}
                    sx={{ color: palette.text_80 }}
                />
            </ListItemButton>
            <Divider sx={{ my: 1, borderColor: palette.divider_8, borderBottomWidth: 0.5 }} />
            {officeFeature.createMenus.map(menu => (
                <ListItemButton
                    key={menu.type}
                    sx={{ px: 6, gap: 2 }}
                    onClick={async () => {
                        onClose();
                        await createNewDocument(menu.type);
                    }}
                >
                    <ListItemIcon sx={{ minWidth: 28 }}>
                        <Image
                            src={getFile(menu.icon).src}
                            alt={menu.type}
                            width={24}
                            height={24}
                        />
                    </ListItemIcon>
                    <ListItemText primary={t(menu.label)} sx={{ color: palette.text_80 }} />
                </ListItemButton>
            ))}
            {intellectEnabled && (
                <ListItemButton
                    sx={{
                        px: 6,
                        gap: 2,
                    }}
                    onClick={async () => {
                        onClose();
                        await createNewDocument('note');
                    }}
                >
                    <ListItemIcon sx={{ minWidth: 28 }}>
                        <Image
                            src={getFile('intellect').src}
                            alt={'folder'}
                            width={24}
                            height={24}
                        />
                    </ListItemIcon>
                    <ListItemText
                        primary={
                            <Stack direction="row" alignItems="center" gap={1}>
                                <Typography variant={typography.body8} color={palette.text_80}>
                                    {t('uploadDialog.newIntellect')}
                                </Typography>
                                <Typography
                                    variant="caption"
                                    sx={{
                                        color: palette.text_80,
                                        position: 'relative',
                                        top: '0px',
                                    }}
                                >
                                    {t('label.beta')}
                                </Typography>
                            </Stack>
                        }
                    />
                </ListItemButton>
            )}
            {(officeFeature.canCreate || intellectEnabled) && (
                <Divider sx={{ my: 1, borderColor: palette.divider_8, borderBottomWidth: 0.5 }} />
            )}
            <ListItemButton
                sx={{ px: 6, gap: 2 }}
                onClick={() => {
                    fileInputRef.current?.click();
                }}
            >
                <input
                    type="file"
                    ref={fileInputRef}
                    style={{ display: 'none' }}
                    multiple
                    onChange={e => {
                        const files = Array.from(e.target.files);
                        if (files.length === 0) return;

                        const uploadItems = files.map(file => ({
                            name: file.name,
                            type: 'file',
                            file: file,
                        }));

                        openDialog('uploadFiles', {
                            uploadItems: uploadItems,
                            reload: () => window.postMessage('drive_reload', '*'),
                        });

                        e.target.value = '';
                        onClose();
                    }}
                />
                <ListItemIcon sx={{ minWidth: 28 }}>
                    {/*<Image src={getFile('xlsx').src} alt={"folder"} width={24} height={24}/>*/}
                </ListItemIcon>
                <ListItemText
                    primary={t('uploadDialog.fileUpload')}
                    sx={{ color: palette.text_80 }}
                />
            </ListItemButton>
            {!isPhone && !isTablet && (
                <ListItemButton
                    sx={{ px: 6, gap: 2 }}
                    onClick={() => {
                        folderInputRef.current?.click();
                    }}
                >
                    <ListItemIcon sx={{ minWidth: 28 }}>
                        {/*<Image src={getFile('xlsx').src} alt={"folder"} width={24} height={24}/>*/}
                    </ListItemIcon>
                    <ListItemText
                        primary={t('uploadDialog.folderUpload')}
                        sx={{ color: palette.text_80 }}
                    />
                </ListItemButton>
            )}
            <input
                type="file"
                ref={folderInputRef}
                style={{ display: 'none' }}
                multiple
                onChange={e => {
                    const files = Array.from(e.target.files);
                    if (files.length === 0) return;

                    const rootNodes = [];

                    files.forEach(file => {
                        const path = file.webkitRelativePath || file.name;
                        const parts = path.split('/').filter(p => p);

                        const fileName = parts[parts.length - 1];
                        const folderParts = parts.slice(0, -1);

                        let currentChildren = rootNodes;

                        folderParts.forEach(part => {
                            let folderNode = currentChildren.find(
                                child => child.name === part && child.type === 'folder'
                            );

                            if (!folderNode) {
                                folderNode = {
                                    name: part,
                                    type: 'folder',
                                    children: [],
                                    file: null,
                                };
                                currentChildren.push(folderNode);
                            }
                            currentChildren = folderNode.children;
                        });

                        if (fileName) {
                            currentChildren.push({
                                name: fileName,
                                type: 'file',
                                file: file,
                            });
                        }
                    });

                    openDialog('uploadFiles', {
                        uploadItems: rootNodes,
                        reload: () => window.postMessage('drive_reload', '*'),
                    });

                    e.target.value = '';
                    onClose();
                }}
                webkitdirectory=""
                directory=""
            />
        </ul>
    );
};
export default StyledDriveContextMenu;
