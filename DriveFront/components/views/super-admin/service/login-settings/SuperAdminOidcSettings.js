import {
    IconButton,
    Radio,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import Box from '@mui/material/Box';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import OidcIdpContextMenu from '@/components/common/OidcIdpContextMenu';
import AddIdpDialog from '@/components/common/dialog/AddIdpDialog';
import AddIcon from '@mui/icons-material/Add';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import StyledBadge from '@/components/common/button/StyledBadge';
import dayjs from 'dayjs';

const STATUS_BADGE = {
    connected: {
        bg: palette.badge_oidc_connected_bg,
        borderColor: palette.badge_oidc_connected_border,
        color: palette.badge_oidc_connected_text,
    },
    disconnected: {
        bg: palette.badge_oidc_disconnected_bg,
        borderColor: palette.badge_oidc_disconnected_border,
        color: palette.badge_oidc_disconnected_text,
    },
};

const TableRoot = styled('div')(() => ({
    width: '100%',
    overflowX: 'auto',

    '& table': {
        width: '100%',
        boxShadow: 'none',
        minWidth: 580,
    },

    '& th': {
        height: 36,
        padding: '0 !important',
        backgroundColor: 'transparent !important',
        borderBottom: 'none !important',
        borderTop: 'none !important',
        verticalAlign: 'top',
        overflow: 'hidden',
    },

    '& td': {
        height: 44,
        padding: '0px 32px 0px 8px',
        background: palette.white,
        borderBottom: `1px ${palette.table_divider_nomal} solid`,
    },

    '& td:first-child': {
        padding: '0 20px',
    },

    '& td:last-child': {
        paddingLeft: 0,
        paddingRight: '8px',
    },
}));

const thInnerSx = {
    height: 30,
    px: '8px',
    display: 'flex',
    alignItems: 'center',
    backgroundColor: palette.white,
    boxShadow: `0px 1px 6px ${palette.control_border_normal}`,
    borderTop: `1px ${palette.divider_8} solid`,
};

export default function SuperAdminOidcSettings({ oidcProviders, setOidcProviders }) {
    const t = useTranslations();
    const { openDialog } = useDialog();

    const [menuAnchor, setMenuAnchor] = useState(null);
    const [menuTargetSeq, setMenuTargetSeq] = useState(null);
    const [addIdpOpen, setAddIdpOpen] = useState(false);

    const displayProviders = oidcProviders;

    const handleActivateOidcProvider = ssoProviderSeq => {
        openDialog('alert', {
            title: t('admin.loginSetting.changeIdpDialog.title'),
            description: t('admin.loginSetting.changeIdpDialog.desc'),
            buttonName: t('common.button.change'),
            maxWidth: 360,
            onClick: () => {
                setOidcProviders(prev =>
                    prev.map(p => ({
                        ...p,
                        status: p.ssoProviderSeq === ssoProviderSeq ? 'ACTIVE' : 'INACTIVE',
                    }))
                );
            },
        });
    };

    return (
        <Box
            sx={{
                width: '100%',
                borderRadius: '6px',
                outline: `1px ${palette.divider_8} solid`,
                outlineOffset: '-1px',
                overflow: 'hidden',
            }}
        >
            {/* 헤더 */}
            <Box
                sx={{
                    py: '16px',
                    pl: '20px',
                    pr: '16px',
                    background: palette.bg_gray,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                }}
            >
                <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <Typography sx={{ typography: 'subtitle1', color: palette.text_100 }}>
                        {t('admin.loginSetting.oidcConfig.title')}
                    </Typography>
                    <Typography sx={{ typography: 'body7', color: palette.text_60 }}>
                        {t('admin.loginSetting.oidcConfig.desc')}
                    </Typography>
                </Box>
                <Box
                    onClick={() => setAddIdpOpen(true)}
                    sx={{
                        height: '28px',
                        pl: '6px',
                        pr: '8px',
                        background: palette.white,
                        borderRadius: '4px',
                        outline: `1px ${palette.control_border_normal} solid`,
                        outlineOffset: '-1px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        cursor: 'pointer',
                        flexShrink: 0,
                    }}
                >
                    <AddIcon sx={{ fontSize: '20px', color: palette.text_80, opacity: 0.8 }} />
                    <Typography
                        sx={{
                            fontSize: '12px',
                            fontWeight: 500,
                            lineHeight: '16px',
                            color: palette.text_80,
                        }}
                    >
                        {t('admin.loginSetting.oidcConfig.addIdpBtn')}
                    </Typography>
                </Box>
            </Box>

            {/* 테이블 */}
            <TableRoot>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={{ width: 64 }}>
                                <Box sx={{ ...thInnerSx, justifyContent: 'center' }}>
                                    <Typography
                                        sx={{ typography: 'body9', color: palette.text_60 }}
                                    >
                                        {t('admin.loginSetting.oidcConfig.table.connectionSetting')}
                                    </Typography>
                                </Box>
                            </TableCell>
                            <TableCell sx={{ width: { xs: '40%', md: 320 } }}>
                                <Box sx={thInnerSx}>
                                    <Typography
                                        sx={{ typography: 'body9', color: palette.text_60 }}
                                    >
                                        {t('admin.loginSetting.oidcConfig.table.name')}
                                    </Typography>
                                </Box>
                            </TableCell>
                            <TableCell sx={{ width: 160 }}>
                                <Box sx={thInnerSx}>
                                    <Typography
                                        sx={{ typography: 'body9', color: palette.text_60 }}
                                    >
                                        {t('admin.loginSetting.oidcConfig.table.registeredAt')}
                                    </Typography>
                                </Box>
                            </TableCell>
                            <TableCell sx={{ width: 88 }}>
                                <Box sx={thInnerSx}>
                                    <Typography
                                        sx={{ typography: 'body9', color: palette.text_60 }}
                                    >
                                        {t('admin.loginSetting.oidcConfig.table.connectionStatus')}
                                    </Typography>
                                </Box>
                            </TableCell>
                            <TableCell>
                                <Box sx={thInnerSx} />
                            </TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {oidcProviders?.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={5} sx={{ borderBottom: 'none', p: 0 }}>
                                    <Box
                                        sx={{
                                            height: 160,
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                        }}
                                    >
                                        <Typography
                                            sx={{ typography: 'body7', color: palette.text_40 }}
                                        >
                                            {t('admin.loginSetting.oidcConfig.table.empty')}
                                        </Typography>
                                    </Box>
                                </TableCell>
                            </TableRow>
                        )}
                        {oidcProviders?.length > 0 &&
                            displayProviders.map(provider => {
                                const isConnected = provider.status === 'ACTIVE';
                                const badge = isConnected
                                    ? STATUS_BADGE.connected
                                    : STATUS_BADGE.disconnected;
                                return (
                                    <TableRow key={provider.ssoProviderSeq}>
                                        {/* 기본: 양쪽 padding 20px, 중앙 정렬 */}
                                        <TableCell align="center">
                                            <Radio
                                                checked={provider.status === 'ACTIVE'}
                                                onChange={() => {
                                                    if (provider.status !== 'ACTIVE') {
                                                        handleActivateOidcProvider(
                                                            provider.ssoProviderSeq
                                                        );
                                                    }
                                                }}
                                                size="small"
                                                sx={{
                                                    p: 0,
                                                    color: palette.control_border_normal,
                                                    '&.Mui-checked': { color: palette.text_80 },
                                                }}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                noWrap
                                                sx={{ typography: 'body7', color: palette.text_80 }}
                                            >
                                                {provider.displayName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                sx={{ typography: 'body9', color: palette.text_60 }}
                                            >
                                                {provider.registerDate
                                                    ? dayjs(provider.registerDate).format(
                                                          'YYYY. M. D. HH:mm'
                                                      )
                                                    : '-'}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <StyledBadge
                                                bg={badge.bg}
                                                borderColor={badge.borderColor}
                                                color={badge.color}
                                            >
                                                {isConnected
                                                    ? t(
                                                          'admin.loginSetting.oidcConfig.table.connected'
                                                      )
                                                    : t(
                                                          'admin.loginSetting.oidcConfig.table.disconnected'
                                                      )}
                                            </StyledBadge>
                                        </TableCell>
                                        {/* 액션: 오른쪽 정렬, pr 8px */}
                                        <TableCell align="right">
                                            <IconButton
                                                size="small"
                                                sx={{ p: '6px', borderRadius: '32px' }}
                                                onClick={e => {
                                                    setMenuAnchor(e.currentTarget);
                                                    setMenuTargetSeq(provider.ssoProviderSeq);
                                                }}
                                            >
                                                <MoreHorizIcon
                                                    sx={{
                                                        fontSize: '20px',
                                                        color: palette.text_80,
                                                        opacity: 0.8,
                                                    }}
                                                />
                                            </IconButton>
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                    </TableBody>
                </Table>
            </TableRoot>

            <OidcIdpContextMenu
                anchorEl={menuAnchor}
                onClose={() => setMenuAnchor(null)}
                provider={oidcProviders.find(p => p.ssoProviderSeq === menuTargetSeq) ?? null}
                onEditSuccess={updatedProvider =>
                    setOidcProviders(prev =>
                        prev.map(p =>
                            p.ssoProviderSeq === updatedProvider.ssoProviderSeq
                                ? updatedProvider
                                : p
                        )
                    )
                }
                onDeleteSuccess={ssoProviderSeq =>
                    setOidcProviders(prev => prev.filter(p => p.ssoProviderSeq !== ssoProviderSeq))
                }
            />

            <AddIdpDialog
                open={addIdpOpen}
                onClose={() => setAddIdpOpen(false)}
                onSuccess={newProvider =>
                    setOidcProviders(prev => [
                        newProvider,
                        ...prev.map(p => ({ ...p, status: 'INACTIVE' })),
                    ])
                }
            />
        </Box>
    );
}
