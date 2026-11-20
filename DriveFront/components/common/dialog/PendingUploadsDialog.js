'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    Typography,
} from '@mui/material';
import { useTranslations } from 'next-intl';
import dayjs from 'dayjs';
import StyledButton from '@/components/common/button/StyledButton';
import convertBytesToReadableSize from '@/utils/math';
import { cancelPendingUpload, listPendingUploads } from '@/utils/chunkUpload';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

/**
 * 진행 중(PENDING) 청크 업로드 목록과 취소.
 * 업로드 시작이 용량 부족으로 거절됐는데 내가 취소할 수 있는 진행 중 업로드가 있을 때 연다
 * (사용량 막대는 확정값만 보여 주므로 "공간이 있는데 왜 안 되지"를 여기서 설명 — 설계 규칙 12).
 *
 * @param sessions 여는 쪽이 이미 읽은 목록(없으면 열 때 읽는다)
 */
export default function PendingUploadsDialog({ open, onClose, sessions: initialSessions }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [sessions, setSessions] = useState(initialSessions ?? null);
    const [busyId, setBusyId] = useState(null);

    const load = useCallback(async () => {
        const list = await listPendingUploads();
        setSessions(list ?? []);
    }, []);

    useEffect(() => {
        if (open && !initialSessions) load();
    }, [open, initialSessions, load]);

    const totalByte = (sessions || []).reduce((sum, s) => sum + (s.size || 0), 0);

    const handleCancel = async sessionId => {
        setBusyId(sessionId);
        const ok = await cancelPendingUpload(sessionId);
        setBusyId(null);
        if (!ok) {
            showSnackbar(t('upload.pending.cancelFail'), 'error');
            return;
        }
        setSessions(prev => (prev || []).filter(s => s.sessionId !== sessionId));
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '520px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle sx={{ p: 0 }}>
                <Typography variant="body1" color={'text_100'}>
                    {t('upload.pending.title')}
                </Typography>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Typography
                    variant="body5"
                    color={'text_80'}
                    sx={{ whiteSpace: 'pre-line' }}
                    component="p"
                >
                    {t('upload.pending.description', {
                        count: (sessions || []).length,
                        size: convertBytesToReadableSize(totalByte),
                    })}
                </Typography>

                <Stack gap={2} sx={{ mt: 4, maxHeight: 280, overflowY: 'auto' }}>
                    {sessions === null && (
                        <Stack alignItems="center" sx={{ py: 4 }}>
                            <CircularProgress size={24} />
                        </Stack>
                    )}
                    {sessions?.length === 0 && (
                        <Typography variant="body6" color={'text_60'}>
                            {t('upload.pending.empty')}
                        </Typography>
                    )}
                    {sessions?.map(s => (
                        <Stack
                            key={s.sessionId}
                            direction="row"
                            alignItems="center"
                            justifyContent="space-between"
                            gap={3}
                        >
                            <Stack sx={{ minWidth: 0, flex: 1 }}>
                                <Typography noWrap variant="body6" color={'text_100'}>
                                    {s.fileName}
                                </Typography>
                                <Typography noWrap variant="body7" color={'text_60'}>
                                    {convertBytesToReadableSize(s.size)} ·{' '}
                                    {t('upload.pending.lastActivity', {
                                        time: dayjs(s.lastActivity).format('YYYY-MM-DD HH:mm'),
                                    })}
                                </Typography>
                            </Stack>
                            <StyledButton
                                onClick={() => handleCancel(s.sessionId)}
                                disabled={busyId === s.sessionId}
                                color={'btn_border'}
                                variant="outlined"
                                size="small"
                                sx={{ flexShrink: 0 }}
                            >
                                <Typography variant={'body6'} color={'text_100'}>
                                    {t('upload.pending.cancel')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    ))}
                </Stack>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 6 }}>
                <StyledButton
                    onClick={onClose}
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 160 } }}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.close')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
