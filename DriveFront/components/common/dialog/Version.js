import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Typography,
    Menu,
    MenuItem,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    TableContainer,
    IconButton,
    Stack,
} from '@mui/material';
import Image from 'next/image';
import React, { useEffect, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import { useTranslations } from 'next-intl';
import api, { getBaseURL } from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import convertBytesToReadableSize from '@/utils/math';
import { useUser } from '@/context/UserContext';
import AlertDialog from '@/components/common/dialog/AlertDialog';
import dayjs from 'dayjs';
import { BaseTooltip } from '../tooltip/BaseTooltip';

const Root = styled('div')(({ theme }) => ({
    table: {
        boxShadow: 'none',
        width: '100%',
        height: '100%',
        border: `1px solid ${palette.table_divider_hover}`,
        borderRadius: '5px',
    },
    tHead: {
        height: 30,
        borderBottom: `1px solid ${palette.table_divider_hover}`,
        borderRight: `none`,
        backgroundColor: palette.white,
    },
    th: {
        ...typography.body9,
        color: palette.text_60,
        borderBottom: `1px solid ${palette.btn_white_bg_hover}`,
    },
    td: {
        padding: '11px 16px 12px',
        ...typography.body10,
        color: palette.text_60,
        cursor: 'pointer',
        height: 56,
        borderBottom: `1px solid ${palette.btn_white_bg_hover}`,
        [theme.breakpoints.down('md')]: {
            height: 82,
            padding: '24px 12px 24px 16px',
            borderBottom: 'none',
        },
    },
}));
export default function VersionDialog({ open, onClose, ...props }) {
    const { item, reload, listPatch } = props;
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const user = useUser();
    // 테넌트 표기는 행(=그 버전을 만든 사용자) 기준이다 — 파일 소유자(item)가 아니라 v.ownerTenantSeq와 비교한다.
    // 익명 작성자는 테넌트가 없어 ownerTenant*가 null일 수 있으므로 값이 있을 때만 괄호를 붙인다.
    const ownerLabel = v => {
        const name = v?.ownerName ?? '';
        const isExternalTenant =
            v?.ownerTenantSeq != null &&
            user?.tenantSeq != null &&
            Number(v.ownerTenantSeq) !== Number(user.tenantSeq);
        return isExternalTenant && v?.ownerTenantName ? `${name}(${v.ownerTenantName})` : name;
    };

    const [anchorEl, setAnchorEl] = useState(null);
    const [selectedDrive, setSelectedDrive] = useState('내 드라이브');
    const [selectedRow, setSelectedRow] = useState();
    const [pfs, setPfs] = useState(undefined);
    const [path, setPath] = useState([]);
    const [versionList, setVersionList] = useState([]);
    const [row, setRow] = useState(null);
    const [alertDialogOpen, setAlertDialogOpen] = useState(false);
    // const user = useUser()
    const ss = item?.sharedByUserSeq || item?.userSeq || undefined;
    const canView = item?.canView || false; // 버전 기록
    const canEdit = item?.canEdit || false; // 버전 복원
    const canDownload = item?.canDownload || false; // 버전 다운로드
    const canDelete = item?.canDelete || false; // 버전 삭제
    const isLocked = item.isStarred === true;

    const getVersionList = async () => {
        if (!canView) return;
        try {
            const res = await api.Get(`resources/${item?.resourceSeq}/versions`, {
                params: {
                    ss: ss,
                    pi: 1,
                    ps: 9999,
                },
            });
            if (res?.status === 200) {
                const data = res.data?.data;
                setVersionList(data);
            }
        } catch (err) {
            console.error('리스트 가져오기 실패 : ', err);
        }
    };

    useEffect(() => {
        getVersionList();
    }, []);

    const handleVersionDownload = async item => {
        if (!canDownload) return;
        const { resourceSeq, resourceVersionSeq } = item;

        try {
            const url = `resources/${resourceSeq}/versions/${resourceVersionSeq}/download-token`;
            const params = {};
            params.ss = ss;

            const res = await api.Get(url, {
                params,
            });

            if (res.status === 200) {
                const signedUrl = getBaseURL() + res.data.data.linkUrl;
                window.location.href = signedUrl;
            }
        } catch (err) {
            console.error('버전 다운로드 실패 : ', err);
            showSnackbar(t('download.single.fail'), 'error');
        }
        handleClose();
    };

    const handleVersionRestore = async item => {
        if (!canEdit) return;
        const { resourceSeq, resourceVersionSeq } = item;

        try {
            const url = `resources/${resourceSeq}/versions/${resourceVersionSeq}/restore`;

            const body = {};
            body.ss = ss;

            const res = await api.Post(url, body);

            if (res.code === 201) {
                showSnackbar(t('restore.success'), 'success');
                getVersionList();
                // 복원으로 크기·수정일이 바뀌므로 해당 행만 갱신
                if (listPatch?.refresh) await listPatch.refresh(item);
            } else if (res?.code === 423) {
                showSnackbar(t('lock.locked'), 'error');
                if (listPatch?.refresh) await listPatch.refresh(item);
                else reload(true);
            } else {
                showSnackbar(t('restore.fail'), 'error');
            }
        } catch (err) {
            console.error('버전 복원 실패 : ', err);
            showSnackbar(t('restore.fail'), 'error');
        }
        handleClose();
    };

    const handleVersionDelete = async item => {
        if (!canDelete) return;
        const { resourceSeq, resourceVersionSeq } = item;

        try {
            const url = `resources/${resourceSeq}/versions/${resourceVersionSeq}`;
            const params = {};
            params.ss = ss;

            const res = await api.Delete(url, { params });

            if (res.status === 200 || res.status === 204) {
                showSnackbar(t('version.delete.single.success'), 'success');
                getVersionList();
            } else {
                showSnackbar(t('version.delete.single.fail'), 'error');
            }
        } catch (err) {
            console.error('버전 삭제 실패 : ', err);
            showSnackbar(t('version.delete.single.fail'), 'error');
        }
        handleClose();
    };

    const handleVersionMultiDelete = async () => {
        try {
            const url = `resources/${item?.resourceSeq}/versions`;
            const params = {};
            params.ss = ss;

            const res = await api.Delete(url, { params });
            if (res.status === 200 || res.status === 204) {
                showSnackbar(t('version.delete.all.success'), 'success');
                getVersionList();
            } else {
                showSnackbar(t('version.delete.all.fail'), 'error');
            }
        } catch (err) {
            console.error('버전 삭제 실패 : ', err);
            showSnackbar(t('version.delete.all.fail'), 'error');
        }
    };

    const handleClick = (e, row) => {
        e.preventDefault();
        e.stopPropagation();
        setAnchorEl(e.currentTarget);
        setSelectedRow(row);
    };

    const handleClose = () => {
        setAnchorEl(null);
        setSelectedRow(null);
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="md"
            scroll="body"
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '720px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle sx={{ p: 0 }}>
                <Stack direction={'row'} justifyContent={'space-between'} py={2}>
                    <Typography variant={'body1'} color={'text_100'}>
                        {t('resourceVersionDialog.title')}
                    </Typography>
                    <Image
                        src={'/common/icon-ic-close.svg'}
                        width={24}
                        height={24}
                        alt={'close'}
                        onClick={onClose}
                        style={{ cursor: 'pointer' }}
                    />
                </Stack>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Root>
                    <TableContainer sx={{ maxHeight: 500 }}>
                        <Table stickyHeader size="mdall" sx={{ mt: 1 }}>
                            <TableHead
                                sx={{
                                    display: {
                                        xs: 'none',
                                        md: 'table-header-group',
                                    },
                                }}
                            >
                                <TableRow>
                                    <TableCell>{t('resourceVersionDialog.version')}</TableCell>
                                    <TableCell>{t('common.label.fileSize')}</TableCell>
                                    <TableCell>{t('resourceVersionDialog.created on')}</TableCell>
                                    <TableCell>{t('resourceVersionDialog.who')}</TableCell>
                                    <TableCell></TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {versionList.map((v, index) => (
                                    <TableRow
                                        key={index}
                                        onMouseEnter={() => setRow(index)}
                                        onMouseLeave={() => {
                                            setRow(null);
                                        }}
                                        sx={{
                                            cursor: 'pointer',
                                            '&:hover': {
                                                background: palette.btn_white_bg_hover,
                                            },
                                        }}
                                    >
                                        <TableCell sx={{ maxWidth: 300, cursor: 'pointer' }}>
                                            <Stack
                                                direction={'row'}
                                                justifyContent={'space-between'}
                                                alignItems={'center'}
                                            >
                                                <Stack direction={'column'} gap={1}>
                                                    <Stack
                                                        direction={'row'}
                                                        alignItems={'center'}
                                                        gap={1.5}
                                                    >
                                                        <Typography
                                                            variant={'body4'}
                                                            color={'text_80'}
                                                        >
                                                            {t('resourceVersionDialog.version')}{' '}
                                                            {v?.versionNumber}
                                                        </Typography>
                                                        {v?.versionType === 'CURRENT' && (
                                                            <Stack
                                                                direction={'row'}
                                                                alignItems={'center'}
                                                                gap={'2px'}
                                                            >
                                                                <svg
                                                                    width="16"
                                                                    height="16"
                                                                    viewBox="0 0 16 16"
                                                                    fill="none"
                                                                    xmlns="http://www.w3.org/2000/svg"
                                                                >
                                                                    <mask
                                                                        id="tvt6hfosda"
                                                                        style={{
                                                                            maskType: 'alpha',
                                                                        }}
                                                                        maskUnits="userSpaceOnUse"
                                                                        x="1"
                                                                        y="1"
                                                                        width="14"
                                                                        height="14"
                                                                    >
                                                                        <circle
                                                                            cx="8"
                                                                            cy="8"
                                                                            r="6.4"
                                                                            fill="#38BE6E"
                                                                        />
                                                                    </mask>
                                                                    <g mask="url(#tvt6hfosda)">
                                                                        <path
                                                                            fill="#4566EA"
                                                                            d="M1.018 1.018h13.964v13.964H1.018z"
                                                                        />
                                                                    </g>
                                                                    <path
                                                                        d="m6.479 9.916 4.2-4.152a.52.52 0 0 1 .374-.164.508.508 0 0 1 .38.164.534.534 0 0 1 0 .773l-4.502 4.467a.617.617 0 0 1-.904 0L3.757 8.71a.519.519 0 0 1-.157-.383.545.545 0 0 1 .168-.39.523.523 0 0 1 .382-.168c.144 0 .271.056.382.168L6.48 9.916z"
                                                                        fill="#fff"
                                                                    />
                                                                </svg>

                                                                <Typography
                                                                    color={'text_tab_selected'}
                                                                    variant={'body9'}
                                                                >
                                                                    Current
                                                                </Typography>
                                                            </Stack>
                                                        )}
                                                    </Stack>
                                                    <Stack
                                                        direction={'row'}
                                                        alignItems={'center'}
                                                        gap={1.5}
                                                        sx={{ display: { xs: 'flex', md: 'none' } }}
                                                    >
                                                        <Typography
                                                            color={'text_60'}
                                                            variant={'body9'}
                                                        >
                                                            {convertBytesToReadableSize(
                                                                v?.sizeByte
                                                            )}
                                                        </Typography>
                                                        <Typography
                                                            color={'text_60'}
                                                            variant={'body9'}
                                                        >
                                                            {v?.versionRegisterDate}
                                                        </Typography>
                                                        <Typography
                                                            color={'text_60'}
                                                            variant={'body9'}
                                                        >
                                                            {ownerLabel(v)}
                                                        </Typography>
                                                    </Stack>
                                                </Stack>
                                                <IconButton
                                                    sx={{ display: { xs: 'block', md: 'none' } }}
                                                    onClick={e => handleClick(e, v)}
                                                >
                                                    <Image
                                                        src="/common/icon-ic-dots-vertical.svg"
                                                        alt="dot"
                                                        id={'dots'}
                                                        width={20}
                                                        height={20}
                                                    />
                                                </IconButton>
                                            </Stack>
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                        >
                                            {convertBytesToReadableSize(v?.sizeByte)}
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                        >
                                            {dayjs(v?.versionRegisterDate).format(
                                                'YYYY. M. D. H:mm'
                                            )}
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                        >
                                            {ownerLabel(v)}
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                minWidth: 150,
                                                display: {
                                                    xs: 'none',
                                                    md: 'table-cell',
                                                },
                                            }}
                                        >
                                            {index === row && (
                                                <Stack
                                                    direction={'row'}
                                                    alignItems={'center'}
                                                    justifyContent={'center'}
                                                    gap={2}
                                                >
                                                    <BaseTooltip
                                                        title={t('tooltip.versionDownload')}
                                                    >
                                                        <IconButton
                                                            sx={{ p: '6px' }}
                                                            disabled={
                                                                v?.versionType === 'CURRENT' ||
                                                                !canDownload
                                                            }
                                                            onClick={() => handleVersionDownload(v)}
                                                        >
                                                            <Image
                                                                src="/common/icon-ic-download.svg"
                                                                alt="download"
                                                                width={20}
                                                                height={20}
                                                            />
                                                        </IconButton>
                                                    </BaseTooltip>
                                                    <BaseTooltip title={t('tooltip.versionDelete')}>
                                                        <IconButton
                                                            sx={{ p: '6px' }}
                                                            disabled={
                                                                v?.versionType === 'CURRENT' ||
                                                                !canDelete
                                                            }
                                                            onClick={() => handleVersionDelete(v)}
                                                        >
                                                            <Image
                                                                src="/common/icon-ic-trash-outline.svg"
                                                                alt="trash"
                                                                width={20}
                                                                height={20}
                                                            />
                                                        </IconButton>
                                                    </BaseTooltip>
                                                    <BaseTooltip
                                                        title={t('tooltip.versionRestore')}
                                                    >
                                                        <IconButton
                                                            sx={{ p: '6px' }}
                                                            disabled={
                                                                v?.versionType === 'CURRENT' ||
                                                                !canEdit ||
                                                                isLocked
                                                            }
                                                            onClick={() => handleVersionRestore(v)}
                                                        >
                                                            <Image
                                                                src="/common/icon-ic-update.svg"
                                                                alt="update"
                                                                width={20}
                                                                height={20}
                                                            />
                                                        </IconButton>
                                                    </BaseTooltip>
                                                </Stack>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Root>
            </DialogContent>
            <DialogActions sx={{ justifyContent: 'space-between' }}>
                <StyledButton
                    color={'btn_border'}
                    variant="outlined"
                    size="mdall"
                    sx={{ minWidth: { xs: '100%', tablet: 'auto' } }}
                    onClick={() => setAlertDialogOpen(true)}
                    disabled={!canDelete}
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('resourceVersionDialog.deletePreviousVersions')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="mdall"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={onClose}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.close')}
                    </Typography>
                </StyledButton>
            </DialogActions>
            <AlertDialog
                open={alertDialogOpen}
                onClose={() => setAlertDialogOpen(false)}
                title={t('deleteVersionConfirmDialog.title')}
                description={t('deleteVersionConfirmDialog.description')}
                buttonName={t('common.action.delete')}
                onConfirm={handleVersionMultiDelete}
            />

            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleClose}
                onClick={handleClose}
                PaperProps={{
                    style: {
                        width: '200px',
                        borderRadius: '8px',
                        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                    },
                }}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
            >
                <MenuItem
                    onClick={e => {
                        e.stopPropagation();
                        handleVersionDownload(selectedRow);
                    }}
                    disabled={selectedRow?.versionType === 'CURRENT' || !canDownload}
                    sx={{
                        fontSize: typography.body9.fontSize,
                        color:
                            selectedRow?.versionType === 'CURRENT' || !canDownload
                                ? palette.text_40
                                : palette.text_80,
                    }}
                >
                    {t('tooltip.versionDownload')}
                </MenuItem>
                <MenuItem
                    onClick={e => {
                        e.stopPropagation();
                        handleVersionDelete(selectedRow);
                    }}
                    disabled={selectedRow?.versionType === 'CURRENT' || !canDelete}
                    sx={{
                        fontSize: typography.body9.fontSize,
                        color:
                            selectedRow?.versionType === 'CURRENT' || !canDelete
                                ? palette.text_40
                                : palette.text_80,
                    }}
                >
                    {t('tooltip.versionDelete')}
                </MenuItem>
                <MenuItem
                    onClick={e => {
                        e.stopPropagation();
                        handleVersionRestore(selectedRow);
                    }}
                    disabled={selectedRow?.versionType === 'CURRENT' || !canEdit || isLocked}
                    sx={{
                        fontSize: typography.body9.fontSize,
                        color:
                            selectedRow?.versionType === 'CURRENT' || !canEdit || isLocked
                                ? palette.text_40
                                : palette.text_80,
                    }}
                >
                    {t('tooltip.versionRestore')}
                </MenuItem>
            </Menu>
        </Dialog>
    );
}
