/** API Key 만료일 수정 — 만료된 키도 미래 날짜로 연장하면 다시 쓸 수 있다. */
import React, { useEffect, useState } from 'react';
import {
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    IconButton,
    Stack,
    Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import dayjs from 'dayjs';
import { useTranslations } from 'next-intl';
import StyledButton from '@/components/common/button/StyledButton';
import StyledDatePicker from '@/components/common/input/StyledDatePicker';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

const formLabelSx = {
    ...typography.body5,
    color: palette.text_80,
    '& .MuiFormLabel-asterisk': {
        color: palette.status_error,
        fontWeight: typography.body4.fontWeight,
    },
};

export default function UpdateApiKeyExpireDateDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { item, reload, listPatch, basePath = 'admins/api-keys' } = props;

    const [expireDate, setExpireDate] = useState(null);

    useEffect(() => {
        if (!open) return;
        setExpireDate(item?.expireDate ? dayjs(item.expireDate) : null);
    }, [open, item?.expireDate]);

    // 지난 날짜는 서버가 거부하므로 오늘 이후만 허용
    const isDisabled =
        expireDate?.isValid?.() !== true || expireDate.endOf('day').isBefore(dayjs());

    const handleSave = async () => {
        // 날짜만 보내고 시각(23:59:59)은 서버가 정한다. 목록 갱신용 값만 그 날의 끝으로 맞춘다
        const nextExpireDate = expireDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss');
        const res = await api.Patch(`${basePath}/${item?.apiKeySeq}/expire-date`, {
            expireDate: expireDate.format('YYYY-MM-DD'),
        });

        if (res?.status === 200) {
            showSnackbar(t('admin.apiKey.updateExpiryDialog.success'), 'success');
            // 목록 전체 재조회 대신 만료일이 바뀐 행만 갱신한다(스크롤 위치 유지)
            if (listPatch?.patchExpireDate)
                listPatch.patchExpireDate(item?.apiKeySeq, nextExpireDate);
            else await reload?.();
            onClose();
        } else {
            showSnackbar(t('admin.apiKey.updateExpiryDialog.fail'), 'error');
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    pt: '32px',
                    px: '32px',
                    pb: '24px',
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
                <Typography variant="body1" color="text_100">
                    {t('admin.apiKey.updateExpiryDialog.title')}
                </Typography>
                <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4, overflow: 'visible' }}>
                <Stack sx={{ pt: '4px', gap: '16px' }}>
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.apiKey.createDialog.expiryLabel')}
                        </FormLabel>
                        <StyledDatePicker
                            value={expireDate}
                            setValue={setExpireDate}
                            minDate={dayjs()}
                            fullWidth
                            height={40}
                            borderRadius={4}
                            iconSize={20}
                        />
                    </FormControl>
                </Stack>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 4 }}>
                <StyledButton
                    onClick={onClose}
                    color="btn_border"
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                >
                    <Typography variant="body5" color="text_100">
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color="btn_point_bg"
                    variant="contained"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleSave}
                    disabled={isDisabled}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
