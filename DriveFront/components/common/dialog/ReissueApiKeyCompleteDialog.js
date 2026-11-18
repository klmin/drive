import React from 'react';
import {
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    IconButton,
    Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import palette from '@/theme/palette';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { copyTextToClipboard } from '@/utils/clipboard';

export default function ReissueApiKeyCompleteDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    // 신규 발급도 같은 화면을 쓴다 — 폐기 안내는 재발급일 때만 보여준다.
    const { apiKey, mode = 'reissue' } = props;
    const isReissue = mode === 'reissue';

    const handleCopy = async () => {
        const success = await copyTextToClipboard(apiKey, t('shareClipboardPrompt.description'));
        showSnackbar(
            t(success ? 'adminApiKeyDialog.copySuccess' : 'clipboard.copyFail'),
            success ? 'success' : 'error'
        );
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            disableEnforceFocus
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
            {/* 제목 + 닫기 버튼 */}
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1">
                    {isReissue
                        ? t('reissueApiKeyCompleteDialog.title')
                        : t('adminApiKeyDialog.completeTitle')}
                </Typography>
                <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                {/* 기존 Key 폐기 안내 — 재발급 전용 */}
                {isReissue && (
                    <Typography
                        variant="body5"
                        color="text_80"
                        sx={{ whiteSpace: 'pre-line', mb: 4, display: 'block' }}
                    >
                        {t('reissueApiKeyCompleteDialog.description')}
                    </Typography>
                )}

                {/* 보안 경고 박스 */}
                <Box
                    sx={{
                        backgroundColor: 'rgba(250, 255, 0, 0.11)',
                        borderRadius: '16px',
                        border: '1px solid #FFC130',
                        pt: '20px',
                        pb: '20px',
                        px: '32px',
                        mb: 4,
                        boxShadow: '0px 1px 4px rgba(0, 0, 0, 0.1)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '24px',
                    }}
                >
                    <Typography variant="body5" color="text_80" sx={{ whiteSpace: 'pre-line' }}>
                        {t('adminApiKeyDialog.completeNotice')}
                    </Typography>
                </Box>

                {/* API Key 표시 */}
                <FormControl fullWidth>
                    <FormLabel>{t('adminApiKeyDialog.apiKey')}</FormLabel>
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'row',
                            alignItems: 'center',
                            gap: '10px',
                            borderRadius: '16px',
                            border: '1px solid #000000',
                            pt: '20px',
                            pb: '20px',
                            px: '32px',
                            boxShadow: '0px 1px 10px 0px rgba(58, 61, 73, 0.12)',
                        }}
                    >
                        <StyledTextField
                            value={apiKey}
                            inputProps={{ readOnly: true }}
                            variant="outlined"
                            height={40}
                            sx={{
                                flex: 1,
                                minWidth: 0,
                                '& .MuiInputBase-input': {
                                    color: palette.text_80,
                                },
                                '& .MuiOutlinedInput-notchedOutline': {
                                    borderColor: palette.control_border_normal,
                                },
                                '& .MuiInputBase-root:hover .MuiOutlinedInput-notchedOutline': {
                                    borderColor: palette.control_border_normal,
                                },
                                '& .MuiInputBase-root.Mui-focused .MuiOutlinedInput-notchedOutline':
                                    {
                                        borderColor: palette.control_border_normal,
                                        borderWidth: '1px',
                                    },
                            }}
                        />
                        <StyledButton
                            color="btn_point_bg"
                            variant="contained"
                            onClick={handleCopy}
                            sx={{ height: 40, minWidth: { xs: '100%', tablet: 'auto' } }}
                        >
                            <Typography variant="body5" color="white">
                                {t('adminApiKeyDialog.copy')}
                            </Typography>
                        </StyledButton>
                    </Box>
                </FormControl>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 4, justifyContent: 'stretch' }}>
                <StyledButton
                    onClick={onClose}
                    color="btn_border"
                    variant="outlined"
                    fullWidth
                    sx={{
                        height: '40px',
                        width: '100%',
                        minWidth: '80px',
                        borderRadius: '4px',
                        pt: '8px',
                        pb: '8px',
                        px: '16px',
                        gap: '10px',
                        backgroundColor: palette.btn_white_bg_normal,
                    }}
                >
                    <Typography variant="body5" color="text_100">
                        {t('adminApiKeyDialog.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
