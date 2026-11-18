'use client';

import React, { useEffect, useState } from 'react';
import {
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography,
    Link,
    Checkbox,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import palette from '@/theme/palette';
import StyledCheckbox from '../button/StyledCheckbox';
import api from '@/utils/api';
import { download, downloadCsvText } from '@/utils/file';
import useClientConfig from '@/hooks/useClientConfig';

export default function BulkUserImportDialog({ open, onClose, reload }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    // SYSTEM_MANAGED 는 안내 메일 발송(se=true)이 막혀 있어(TENANT_011) 체크 해제·비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    const [sending, setSending] = useState(false);
    const [failed, setFailed] = useState(false); // 실패/부분성공 여부 + 결과 CSV URL
    const [failCsv, setFailCsv] = useState(null); // 서버 응답 저장 (결과 CSV 다운로드용)

    const [formData, setFormData] = useState({
        file: '',
        se: true,
    });
    const sendEmail = formData.se && !isSystemManaged;

    const handleChange = e => {
        const { name, value, files, checked } = e.target;

        if (name === 'file') {
            const file = files?.[0];
            if (!file) return;

            setFormData(prev => ({
                ...prev,
                file: file,
            }));
            // value = ''; // 동일 파일 재선택 가능하도록 초기화
            return;
        }

        if (name === 'sendEmail') {
            setFormData(prev => ({
                ...prev,
                se: checked,
            }));
            return;
        }
    };

    // CSV 업로드
    const handleBulkUserImport = async () => {
        if (!formData.file) {
            showSnackbar(t('bulkUserImportDialog.errors.emptyFile'), 'error');
            return;
        }

        const res = await api.PostForm(
            'admins/users/import/csv',
            {
                file: formData.file,
                se: sendEmail ? 'true' : 'false',
            },
            null,
            { __roleType: 'ADMIN' },
            true
        );

        if (res?.status === 201) {
            showSnackbar(t('bulkUserImportDialog.result.success'), 'success');
            setFailed(false);
            await reload(true);
        } else {
            setFailCsv(res.response);
            // console.log('res', res);
            setFailed(true);
            showSnackbar(t('bulkUserImportDialog.result.failed'), 'error');
        }
    };

    // CSV 템플릿 파일 다운로드
    const handleDownloadTemplate = async () => {
        try {
            const res = await api.Get('admins/users/import/csv/sample', {});
            console.log(res);
            if (res.status === 200) {
                download(res);
            } else {
                showSnackbar(t('bulkUserImportDialog.errors.downloadTemplateFailed'), 'error');
            }
        } catch (error) {
            console.error('Download failed:', error);
        }
    };

    // 결과 CSV 파일 다운로드
    const handleDownloadResultCsv = async () => {
        downloadCsvText(failCsv);
    };

    const handleConfirm = async () => {
        try {
            setSending(true);
            await handleBulkUserImport();
        } finally {
            setSending(false);
        }
    };

    useEffect(() => {
        // console.log(formData)
    }, [formData, failCsv]);

    return (
        <Dialog
            open={open}
            disableEscapeKeyDown // esc 키 막기
            onClose={(event, reason) => {
                // 딤 클릭/ esc 키로 들어오는 onClose 무시
                if (reason === 'backdropClick' || reason === 'escapeKeyDown') {
                    return; // 아무 것도 안 함
                }
                onClose();
            }}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 6,
                    margin: 0,
                    maxWidth: '820px !important', // 예시 이미지 폭이 조금 넓어 보여 확장
                    width: '100%',
                },
            }}
        >
            {/* 제목 + 우측 로딩 스피너 */}
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    mb: 2,
                }}
            >
                <Typography
                    variant="body1"
                    color={'text_100'}
                    sx={{ whiteSpace: 'pre-wrap', overflow: 'hidden' }}
                >
                    {t('bulkUserImportDialog.title')}
                </Typography>
                {sending && <CircularProgress size={28} color="inherit" />}
            </DialogTitle>

            {/* 서브설명 & 필수노트 */}
            <DialogContent sx={{ p: 0 }}>
                <Stack spacing={0.5}>
                    <Typography variant="body5" color={'text_80'} sx={{ whiteSpace: 'pre-line' }}>
                        {t('bulkUserImportDialog.subtitle')}
                    </Typography>
                    <Typography
                        variant="body5"
                        color={'text_80'}
                        sx={{ whiteSpace: 'pre-line', mt: 0.5 }}
                    >
                        {t('bulkUserImportDialog.requiredNote')}
                    </Typography>
                </Stack>
            </DialogContent>

            {/* 불릿 규칙 섹션 */}
            <DialogContent sx={{ p: 0, mt: 3 }}>
                <Box component="ul" sx={{ pl: 2.5, m: 0 }}>
                    <ul>
                        <li>
                            <Typography variant="body5" color={'text_80'}>
                                {t('bulkUserImportDialog.rules.UTF-8')}
                            </Typography>
                        </li>
                        <li>
                            <Typography variant="body5" color={'text_80'}>
                                {t('bulkUserImportDialog.rules.email')}
                            </Typography>
                        </li>
                        <li>
                            <Typography variant="body5" color={'text_80'}>
                                {t('common.validate.password')}
                            </Typography>
                        </li>
                        <li>
                            <Typography variant="body5" color={'text_80'}>
                                {t('bulkUserImportDialog.rules.storage')}
                            </Typography>
                        </li>
                    </ul>
                </Box>
            </DialogContent>

            {/* 예시 테이블 */}
            <DialogContent sx={{ p: 0, mt: 3 }}>
                <Table
                    size="small"
                    sx={{
                        border: `1px solid ${palette.divider_8}`,
                        '& th, & td': { borderRight: `1px solid ${palette.divider_8}` },
                        '& th:last-of-type, & td:last-of-type': { borderRight: 'none' },
                    }}
                >
                    <TableHead sx={{ backgroundColor: palette.table_bg_hover }}>
                        <TableRow>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('bulkUserImportDialog.table.header.userId')}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('bulkUserImportDialog.table.header.password')}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('bulkUserImportDialog.table.header.displayName')}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('bulkUserImportDialog.table.header.storageGb')}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('bulkUserImportDialog.table.header.countryCode')}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    {t('common.label.phone')}
                                </Typography>
                            </TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        <TableRow>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    jweber@example.com
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    Passw0rd1!
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    James Weber
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    5
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    +82
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    010-1234-5678
                                </Typography>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    kjones@example.com
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    Passw0rd2!
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    Katie Jones
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    10
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    +1
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="body5" color={'text_80'}>
                                    5555551234
                                </Typography>
                            </TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </DialogContent>

            {/* 템플릿 다운로드 한 줄 안내 */}
            <DialogContent sx={{ p: 0, mt: 3 }}>
                <Box component="ul" sx={{ pl: 2.5, m: 0 }}>
                    <Typography variant="body5" color={'text_80'}>
                        {/* "CSV 템플릿 파일을 다운로드하여 작성 후 업로드하세요." */}
                        {t('bulkUserImportDialog.csv.hint')}{' '}
                        <Link
                            component="button"
                            type="button"
                            underline="always"
                            onClick={handleDownloadTemplate}
                        >
                            {t('common.action.download')}
                        </Link>
                    </Typography>
                </Box>
            </DialogContent>

            {/* 파일 선택 & 결과 CSV 다운로드 */}
            <DialogContent sx={{ p: 0, mt: 2 }}>
                <Stack direction="row" alignItems="center" spacing={2}>
                    <StyledButton
                        variant="contained"
                        component="label"
                        sx={{
                            backgroundColor: palette.btn_white_bg_normal,
                            color: 'text.primary',
                            boxShadow: 'none',
                            border: `1px solid ${palette.control_border_normal}`,
                            '&:hover': { backgroundColor: palette.btn_white_bg_hover },
                            px: 2.5,
                            minWidth: { xs: '100%', tablet: 'auto' },
                        }}
                    >
                        <input
                            type="file"
                            accept=".csv"
                            style={{ display: 'none' }}
                            name="file"
                            onChange={handleChange}
                        />
                        <Typography variant="body9" color={'text_80'}>
                            {t('bulkUserImportDialog.uploader.button')}
                        </Typography>
                    </StyledButton>

                    {/* 파일명 표시(없으면 '선택된 파일 없음') */}
                    {formData.file ? (
                        <Chip
                            size="small"
                            label={
                                <Typography variant="body6" color="text_80">
                                    {formData.file.name}
                                </Typography>
                            }
                            sx={{ maxWidth: 280 }}
                        />
                    ) : (
                        <Typography variant="body6" color="text_60">
                            {t('bulkUserImportDialog.uploader.noFile')}
                        </Typography>
                    )}
                </Stack>

                {/* 실패 시 안내 메시지 */}
                {failed && (
                    <Box component="ul" sx={{ pl: 2.5, m: 0 }}>
                        <Stack spacing={0.5} sx={{ mt: 2 }}>
                            <Typography variant="body6" sx={{ color: palette.status_error }}>
                                {t('bulkUserImportDialog.result.failed')}
                            </Typography>
                            <Typography variant="body6" sx={{ color: palette.status_error }}>
                                {t('bulkUserImportDialog.result.seeCsvHint')}
                                {'  '}
                                <Link
                                    component="button"
                                    type="button"
                                    underline="always"
                                    onClick={handleDownloadResultCsv}
                                >
                                    {t('bulkUserImportDialog.result.downloadResultCsv')}
                                </Link>
                            </Typography>
                        </Stack>
                    </Box>
                )}
            </DialogContent>

            {/* 이메일 발송 체크 */}
            <DialogContent sx={{ p: 0, mt: 2 }}>
                <FormControlLabel
                    control={
                        <StyledCheckbox
                            checked={sendEmail}
                            onChange={handleChange}
                            name="sendEmail"
                            disabled={isSystemManaged}
                        />
                    }
                    label={
                        <Typography variant={'body5'} color={'text_80'}>
                            {t('shareAddUserDialog.sendEmail')}
                        </Typography>
                    }
                    sx={{ mt: 2, pl: 1.8, gap: 2, opacity: isSystemManaged ? 0.5 : 1 }}
                />
            </DialogContent>

            {/* 액션 버튼 */}
            <DialogActions sx={{ mt: 2, p: 0 }}>
                <StyledButton
                    onClick={onClose}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    size="small"
                    disabled={sending} // 로딩 중 버튼 비활성화
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.close')}
                    </Typography>
                </StyledButton>

                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleConfirm}
                    disabled={sending || !formData.file} // 로딩 중 또는 파일 없으면 버튼 비활성화
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('bulkUserImportDialog.buttons.import')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
