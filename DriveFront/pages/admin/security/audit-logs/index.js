/**
 * 감사 로그 페이지 — 헤더(제목 + 내보내기) + 필터 + 목록.
 *
 * 내보내기는 화면 URL 쿼리(필터의 단일 소스, AuditLogs.js 참고)를 그대로 API 파라미터로 변환해
 * GET admins/audit-logs/export를 부른다 — 화면에 보이는 조건과 파일 조건이 항상 같아야 해서다.
 * 기간 안의 전체 행이 한 파일에 담기며(거버넌스 :export-range), 기간 필터는 이미 최대 3개월로 묶여 있다.
 */
import React, { useState } from 'react';
import Head from 'next/head';
import Image from 'next/image';
import { useRouter } from '@/hooks/useRouter';
import { Box, Button, CircularProgress, Stack, ThemeProvider, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { withAuditLogTheme } from '@/theme';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import api from '@/utils/api';
import { extractFilename } from '@/utils/file';
import { parseAuditLogFilters } from '@/utils/queryString';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import AuditLogs from '@/components/views/admin/security/audit-logs/AuditLogs';
import auditLogTokens from '@/components/views/admin/security/audit-logs/auditLogTokens';
import { buildAuditLogApiParams } from '@/components/views/admin/security/audit-logs/auditLogFormat';

const headerButtonSx = {
    height: 32,
    py: '4px',
    pl: 2,
    pr: 3,
    gap: 1,
    borderRadius: '6px',
    // 테두리를 안쪽에 — border면 높이·폭에 1px씩 더해진다
    outline: `1px solid ${auditLogTokens.buttonOutlinedBorder}`,
    outlineOffset: '-1px',
    backgroundColor: palette.white,
    boxShadow: auditLogTokens.buttonShadow,
    color: auditLogTokens.textSecondary,
    '&:hover': { backgroundColor: palette.btn_white_bg_hover },
};

export default function AdminAuditLogs() {
    const t = useTranslations();
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();
    const { showSnackbar } = useSnackbarContext();
    const [exporting, setExporting] = useState(false);

    useVvh();

    const handleExportCsv = async () => {
        setExporting(true);
        try {
            const params = buildAuditLogApiParams(parseAuditLogFilters(router.query));
            const res = await api.Get('admins/audit-logs/export', {
                params,
                responseType: 'blob',
            });
            // TODO: 문구 정의 필요
            if (!(res?.status === 200 && res.data instanceof Blob)) {
                throw new Error('문구 정의 필요');
            }

            const fileName = extractFilename(res.headers);
            const url = window.URL.createObjectURL(res.data);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', fileName);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            // TODO: 문구 정의 필요
            showSnackbar(err?.message || '문구 정의 필요', 'error');
        } finally {
            setExporting(false);
        }
    };

    return (
        // 드롭다운 행 높이를 시안(32)으로 — 기본 테마는 36/40이다
        <ThemeProvider theme={withAuditLogTheme}>
            <Head>
                <title>{t('meta.admin', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                sx={{
                    width: '100%',
                    // main이 앱바 높이만큼 밀려 있어 그만큼 빼야 바닥이 뷰포트 안에 들어온다.
                    height: 'calc(var(--vvh, 1vh) * 100 - var(--admin-appbar-h, 0px))',
                    // 페이지는 가로·세로 모두 스크롤하지 않는다 — 넘치는 목록은 카드 안에서 스크롤한다
                    overflow: 'hidden',
                    minWidth: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 3,
                    pt: '10px',
                    pb: 8,
                    px: 8,
                    backgroundColor: palette.white,
                    borderBottomLeftRadius: '16px',
                    borderBottomRightRadius: '16px',
                }}
            >
                <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{ px: 1 }}
                >
                    <Typography
                        sx={{
                            fontSize: 17,
                            fontWeight: 600,
                            lineHeight: '24px',
                            color: auditLogTokens.textSecondary,
                        }}
                    >
                        {t('admin.auditLog.title')}
                    </Typography>
                    <Stack direction="row" alignItems="center" gap={2}>
                        <Button
                            sx={headerButtonSx}
                            onClick={handleExportCsv}
                            disabled={exporting}
                            disableRipple
                        >
                            {exporting ? (
                                <CircularProgress
                                    size={16}
                                    sx={{ color: auditLogTokens.textSecondary }}
                                />
                            ) : (
                                <Image
                                    src="/common/icon-ic-export.svg"
                                    alt=""
                                    width={18}
                                    height={18}
                                />
                            )}
                            <Typography variant="body6_tight">
                                {t('admin.auditLog.exportCsvBtn')}
                            </Typography>
                        </Button>
                    </Stack>
                </Stack>

                <AuditLogs />
            </Box>
        </ThemeProvider>
    );
}
