import React, { useEffect, useRef, useState } from 'react';
import { Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import checkExists from '@/utils/checkExists';
import { useDialog } from '@/provider/DialogProvider';
import { useResources } from '@/provider/ResourceProvider';

const INVALID_CHARS_TEST = /[\\/:*?"<>|]/;
const RESERVED_WORDS = [
    'CON',
    'PRN',
    'AUX',
    'NUL',
    ...Array.from({ length: 9 }, (_, i) => `COM${i + 1}`),
    ...Array.from({ length: 9 }, (_, i) => `LPT${i + 1}`),
];

export default function FolderCreateDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();
    const { reloadResources, reloadLibraries, pfs, ss } = props;
    const inputRef = useRef(null);
    const { openDialog } = useDialog();

    const [name, setName] = useState(t('newFolderDialog.defaultFolderName'));
    const [error, setError] = useState('');

    const { getResources } = useResources(); // 새폴더 생성 시 좌측 폴더 트리 reload 필요

    const checkValidation = val => {
        const trimmed = val.trim();

        if (val !== trimmed) {
            setError(t('nameValidation.leadingOrTrailingSpaces'));
            return false;
        }

        if (trimmed.length > 255) {
            setError(t('nameValidation.exceedsMaxLength'));
            return false;
        }

        if (INVALID_CHARS_TEST.test(val)) {
            setError(t('nameValidation.invalidSpecialChars'));
            return false;
        }

        if (trimmed.startsWith('.') || trimmed.endsWith('.')) {
            setError(t('nameValidation.leadingOrTrailingDot'));
            return false;
        }

        const upperVal = trimmed.toUpperCase();
        if (RESERVED_WORDS.includes(upperVal)) {
            setError(t('nameValidation.windowsReservedName'));
            return false;
        }

        return true;
    };

    const handleChange = e => {
        let val = e.target.value;

        setName(val);
        const check = checkValidation(val);

        if (check) {
            setError('');
        }
    };

    const handleCreateFolder = async () => {
        if (error) return;
        try {
            const createFunc = async () => {
                const res = await api.Post('folders', {
                    fn: name,
                    pfs: pfs,
                    ss: ss,
                });

                if (res?.code === 201) {
                    showSnackbar(t('newFolderCreate.success'), 'success');
                    if (reloadResources) {
                        await reloadResources(true);
                    }
                    console.log('pfs', pfs);
                    console.log('router.query.pfs', router.query.pfs);
                    console.log('ddd', pfs === router.query.pfs);
                    // 현재 위치 문서함에서 새 폴더 생성이면 문서함 목록도 reload
                    if (reloadLibraries && pfs === (router.query.pfs || '')) {
                        await reloadLibraries(true);
                    } else if (pfs === (router.query.pfs || '')) {
                        const root = router.asPath.replace(/^\//, '').split(/[/?#]/)[0];

                        if (root === 'drive' || root === 'shared') {
                            router.push(`/${root}?pfs=${pfs}&ss=${ss}`);
                        }
                    }
                    await getResources();
                    onClose();
                } else {
                    showSnackbar(t('newFolderCreate.fail'), 'error');
                }
            };

            const exists = await checkExists(
                {
                    userSeq: undefined,
                    resourceName: name,
                    resourceType: 'FOLDER',
                    ss: ss,
                },
                pfs,
                createFunc
            );

            if (exists?.resourceSeq) {
                const userConfirmed = await openDialog('alert', {
                    title: t('duplicateDialog.text1', {
                        resourceName: name,
                        resourceType: 'FOLDER',
                    }),
                    description: t('duplicateDialog.text2'),
                    onClick: async () => {
                        await createFunc();
                        onClose();
                    },
                    buttonName: t('common.button.upload'),
                });

                if (!userConfirmed) {
                    onClose();
                    return;
                }
            }
        } catch (error) {
            console.error(error);
        }
    };

    useEffect(() => {
        if (!open) return;
        setTimeout(() => {
            inputRef.current?.focus();
            inputRef.current?.select();
        }, 0);
    }, [open]);

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1">{t('newFolderDialog.title')}</Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4, overflow: 'visible' }}>
                <StyledTextField
                    placeholder={t('newFolderDialog.defaultFolderName')}
                    size="small"
                    value={name}
                    ref={inputRef}
                    onChange={handleChange}
                    onKeyDown={async e => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            await handleCreateFolder();
                        }
                    }}
                    variant="outlined"
                    sx={{ width: '100%' }}
                    height={40}
                    error={!!error}
                    errorMessage={error}
                />
            </DialogContent>
            <DialogActions>
                <StyledButton
                    onClick={onClose}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    size="small"
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    disabled={error || !name}
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleCreateFolder}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
