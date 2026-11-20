import React, { useEffect, useRef, useState } from 'react';
import {
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    InputAdornment,
    Typography,
} from '@mui/material';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { registerRecent } from '@/apis/recentApi';
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

export default function FileNameDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { item, reload, listPatch } = props;
    const inputRef = useRef(null);
    const { refreshResources } = useResources();

    const [name, setName] = useState('');
    const [error, setError] = useState('');
    // 고정 확장자(서버 ext 기준, 확장자 없는 파일이면 ''). null = 조회 중
    const [extension, setExtension] = useState(null);

    const checkValidation = val => {
        const trimmed = val.trim();

        if (val !== trimmed) {
            setError(t('nameValidation.leadingOrTrailingSpaces'));
            return false;
        }

        // 저장 이름 = 입력 이름 + 고정 확장자
        const fullLength = extension ? trimmed.length + extension.length + 1 : trimmed.length;
        if (fullLength > 255) {
            setError(t('nameValidation.exceedsMaxLength'));
            return false;
        }

        if (INVALID_CHARS_TEST.test(val)) {
            setError(t('nameValidation.invalidSpecialChars'));
            return false;
        }

        // 확장자가 붙으면 입력 이름 끝의 마침표는 저장 이름 끝이 아니다
        if (trimmed.startsWith('.') || (!extension && trimmed.endsWith('.'))) {
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

    const handleChangeFileName = async () => {
        if (extension === null) return;
        const res = await (item?.resourceType === 'FOLDER'
            ? api.Patch(`folders/${item?.resourceSeq}/rename`, {
                  ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                  rn: name,
              })
            : api.Patch(`files/${item?.resourceSeq}/rename`, {
                  ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                  rn: extension ? `${name}.${extension}` : name,
              }));

        if (res.status === 200 || res.status === 201) {
            if (item?.resourceType === 'FILE') {
                const registered = await registerRecent(
                    item.resourceSeq,
                    item.sharedByUserSeq || item.userSeq
                );
                if (!registered) {
                    showSnackbar(t('common.toast.changeFail'), 'error');
                }
            } else {
                await refreshResources();
            }
            showSnackbar(t('common.toast.changeSuccess'), 'success');
            onClose();
        } else if (res?.code === 423) {
            showSnackbar(t('lock.locked'), 'error');
            onClose();
        } else if (res?.errorCode === 'RESOURCE_006') {
            // 같은 위치에 같은 이름이 이미 있음
            showSnackbar(t('user.fileList.toast.renameFailDuplicate'), 'error');
        } else {
            showSnackbar(t('common.toast.changeFail'), 'error');
        }

        // 목록 전체 재조회 대신 이름이 바뀐 행만 교체한다(스크롤 위치·선택 상태 유지)
        if (listPatch?.refresh) await listPatch.refresh(item);
        else await reload(true);
    };

    // 확장자는 이름의 마지막 점이 아니라 서버 ext 기준 — 확장자 없는 파일의 "a.txt" 는 이름 전체
    useEffect(() => {
        if (!open || !item) return;
        if (item.resourceType === 'FOLDER') {
            setExtension('');
            setName(item.resourceName);
            return;
        }
        const apply = ext => {
            const fullName = item.resourceName;
            const suffix = `.${ext}`;
            const hasSuffix = ext && fullName.toLowerCase().endsWith(suffix.toLowerCase());
            setExtension(ext);
            setName(hasSuffix ? fullName.slice(0, -suffix.length) : fullName);
        };
        // 목록 항목에 ext 가 있으면 그대로, 없으면 상세 조회(실패 시 확인 버튼 비활성 유지)
        if (typeof item.ext === 'string') {
            apply(item.ext);
            return;
        }
        let cancelled = false;
        setExtension(null);
        getResourceDetail(item.resourceSeq, { ss: item.sharedByUserSeq || item.userSeq }).then(
            detail => {
                if (cancelled || !detail) return;
                // ext 없음('')은 이름 전체, null 은 서버와 같이 마지막 점 기준
                const fullName = item.resourceName;
                apply(detail.ext ?? (fullName.includes('.') ? fullName.split('.').pop() : ''));
            }
        );
        return () => {
            cancelled = true;
        };
    }, [open, item]);

    useEffect(() => {
        if (!open || extension === null) return;
        setTimeout(() => {
            inputRef.current?.focus();
            inputRef.current?.select();
        }, 0);
    }, [open, extension]);

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
                <Typography variant="body1">{t('renameDialog.title')}</Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4, overflow: 'visible' }}>
                <StyledTextField
                    placeholder={t('uploadDialog.newDocs')}
                    size="small"
                    value={name}
                    ref={inputRef}
                    onChange={handleChange}
                    onKeyDown={async e => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            await handleChangeFileName();
                        }
                    }}
                    error={!!error}
                    errorMessage={error}
                    variant="outlined"
                    sx={{ width: '100%' }}
                    height={40}
                    InputProps={
                        item?.resourceType === 'FILE' &&
                        !!extension && {
                            endAdornment: (
                                <InputAdornment position="end">
                                    <Typography variant={'body3'} color={'text_80'}>
                                        .{extension}
                                    </Typography>
                                </InputAdornment>
                            ),
                        }
                    }
                />
            </DialogContent>
            <DialogActions>
                <StyledButton
                    onClick={onClose}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    disabled={error || !name || extension === null}
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleChangeFileName}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
