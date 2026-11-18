'use client';

import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Box,
    Typography,
    Menu,
    MenuItem,
    Breadcrumbs,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    TableContainer,
    IconButton,
    Stack,
} from '@mui/material';
import FolderIcon from '@mui/icons-material/Folder';
import Image from 'next/image';
import React, { useEffect, useRef, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import FILES, { getFile } from '@/constants/FILES';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import DialogPath from '@/components/views/DialogPath';
import FolderCreateDialog from '@/components/common/dialog/FolderCreateDialog';
import dayjs from 'dayjs';

const Root = styled('div')(({ theme }) => ({
    border: `1px solid ${palette.table_divider_hover}`,
    minHeight: 440,
    borderRadius: '4px',
    table: {
        boxShadow: 'none',
        width: '100%',
        height: '100%',
    },
    tHead: {
        height: 30,
        borderBottom: `1px solid ${palette.table_divider_hover}`,
        borderRight: `none`,
        backgroundColor: palette.white,
    },
    th: {
        padding: '3px 23px 3px 16px',
        ...typography.body9,
        color: palette.text_60,
        [theme.breakpoints.down('md')]: {
            padding: '8px 16px',
        },
    },
    td: {
        padding: '10px 16px 10px 12px',
        ...typography.body10,
        color: palette.text_80,
        cursor: 'pointer',
        borderBottom: `1px solid ${palette.table_divider_nomal}`,
        [theme.breakpoints.down('md')]: {
            height: 72,
            padding: '8px 16px',
        },
    },
}));

export default function FolderFixDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [anchorEl, setAnchorEl] = useState(null);
    const [selectedDrive, setSelectedDrive] = useState('내 드라이브');
    const [folderList, setFolderList] = useState([]);
    const [pfs, setPfs] = useState(undefined);
    const [ss, setSs] = useState(undefined);
    const [path, setPath] = useState([]);

    const buttonRef = useRef(null);

    const { reload } = props;

    const handleMenuOpen = e => setAnchorEl(e.currentTarget);
    const handleMenuClose = drive => {
        if (drive) setSelectedDrive(drive);
        setPfs(undefined);
        setSs(undefined);
        setPath([]);
        setAnchorEl(null);
    };

    const getResourceList = async () => {
        try {
            const res =
                selectedDrive === '내 드라이브'
                    ? await api.Get('resources', {
                          params: {
                              st: 'MY',
                              pi: 1,
                              ps: 9999,
                              sb: 'name',
                              pfs,
                          },
                      })
                    : await api.Get(!pfs ? 'shared' : `resources`, {
                          params: { st: 'MY', pi: 1, ps: 9999, pfs, ss },
                      });
            if (res?.status === 200) {
                const data = res.data?.data;
                setFolderList(data);
            }
        } catch (err) {
            console.error('리스트 가져오기 실패:', err);
        }
    };

    useEffect(() => {
        getResourceList();
    }, [selectedDrive, pfs]);

    const getPinnedList = async () => {
        const res = await api.Get('pinned-folders', {});

        let pinnedList = [];
        if (res?.status === 200) {
            pinnedList = res.data?.data || [];
        }

        return pinnedList;
    };

    const addFolder = async () => {
        const folderItem = path[path.length - 1];
        const pinnedList = await getPinnedList();

        const isAlreadyPinned = pinnedList.some(
            item => item.resourceSeq === folderItem?.resourceSeq
        );
        if (isAlreadyPinned) {
            showSnackbar(t('pinnedFolder.add.already'), 'error');
            return;
        }

        const res = await api.Post(`pinned-folders`, {
            rs: folderItem?.resourceSeq,
            ss: folderItem?.sharedByUserSeq || folderItem?.userSeq || '',
        });

        if (res?.code === 201) {
            showSnackbar(t('pinnedFolder.add.success'), 'success');
            reload();
        } else {
            showSnackbar(t('pinnedFolder.add.fail'), 'error');
        }
        onClose();
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="sm"
            scroll="body"
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '640px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle sx={{ p: 0 }}>
                <Typography variant={'body1'} color={'text_100'}>
                    {t('SelectPinnedFolderDialog.title')}
                </Typography>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Box display="flex" alignItems="center" flexWrap="noWrap" mb={2}>
                    <StyledButton
                        variant={anchorEl ? 'contained' : 'text'}
                        size="small"
                        color="btn_white_bg_selected"
                        endIcon={
                            <Image
                                src={'/common/chevron-chevron-filled-12-down.svg'}
                                width={12}
                                height={12}
                                alt={'down'}
                            />
                        }
                        onClick={handleMenuOpen}
                        sx={{ height: 32, fontSize: 14, px: 3, mr: 1.5 }}
                    >
                        <Typography variant={'body7'} color={'text_80'}>
                            {selectedDrive === '내 드라이브'
                                ? t('common.label.myDrive')
                                : t('common.label.shared')}
                        </Typography>
                    </StyledButton>
                    <DialogPath resources={path} setPfs={setPfs} setPath={setPath} />
                </Box>
                <Menu
                    anchorEl={anchorEl}
                    open={Boolean(anchorEl)}
                    onClose={() => handleMenuClose()}
                    PaperProps={{
                        sx: {
                            mt: 1,
                            minWidth: 125,
                            boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        },
                    }}
                >
                    <MenuItem onClick={() => handleMenuClose('내 드라이브')}>
                        {t('common.label.myDrive')}
                    </MenuItem>
                    <MenuItem onClick={() => handleMenuClose('공유 문서함')}>
                        {t('common.label.shared')}
                    </MenuItem>
                </Menu>

                {/* Folder List */}
                <Root>
                    <TableContainer sx={{ maxHeight: 440 }}>
                        <Table stickyHeader size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>{t('data.name')}</TableCell>
                                    <TableCell
                                        align={'right'}
                                        sx={{
                                            display: {
                                                xs: 'none',
                                                md: 'table-cell',
                                            },
                                        }}
                                    >
                                        {t('data.lastModified')}
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {folderList.map(
                                    (folder, index) =>
                                        (folder?.resourceType === 'FOLDER' ||
                                            folder?.resourceType === 'sharedFolder') && (
                                            <TableRow
                                                key={index}
                                                onClick={() => {
                                                    setPfs(folder?.resourceSeq);
                                                    setSs(
                                                        folder?.sharedByUserSeq ||
                                                            folder?.userSeq ||
                                                            undefined
                                                    );
                                                    setPath([...path, folder]);
                                                }}
                                            >
                                                <TableCell
                                                    sx={{ maxWidth: 300, cursor: 'pointer' }}
                                                >
                                                    <Box display="flex" alignItems="center" gap={3}>
                                                        <Image
                                                            src={getFile('folder').src}
                                                            alt={'folder'}
                                                            width={24}
                                                            height={24}
                                                        />
                                                        <Typography
                                                            variant={'body5'}
                                                            color={'text_80'}
                                                            sx={{
                                                                overflow: 'hidden',
                                                                textOverflow: 'ellipsis',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            <Stack direction={'column'} spacing={2}>
                                                                <Typography
                                                                    variant={'body9'}
                                                                    color={'text_80'}
                                                                >
                                                                    {folder.resourceName}
                                                                </Typography>
                                                                <Typography
                                                                    sx={{
                                                                        display: {
                                                                            xs: 'block',
                                                                            md: 'none',
                                                                        },
                                                                    }}
                                                                    color={'text_40'}
                                                                    variant={'body9'}
                                                                >
                                                                    {dayjs(
                                                                        folder?.resourceUpdateDate
                                                                    ).format('YYYY. M. D. H:mm')}
                                                                </Typography>
                                                            </Stack>
                                                        </Typography>
                                                    </Box>
                                                </TableCell>
                                                <TableCell
                                                    align={'right'}
                                                    sx={{
                                                        display: {
                                                            xs: 'none',
                                                            md: 'table-cell',
                                                        },
                                                    }}
                                                >
                                                    <Typography color={'text_40'} variant={'body9'}>
                                                        {dayjs(folder?.resourceUpdateDate).format(
                                                            'YYYY. M. D. H:mm'
                                                        )}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )
                                )}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Root>
            </DialogContent>

            <DialogActions>
                <Stack
                    sx={{ width: '100%' }}
                    direction={{
                        xs: 'column',
                        tablet: 'row',
                    }}
                    justifyContent={{
                        tablet: 'flex-end',
                        xs: 'center',
                    }}
                    spacing={1}
                >
                    <StyledButton
                        onClick={onClose}
                        color={'btn_border'}
                        variant="outlined"
                        size="small"
                        sx={{
                            minWidth: { xs: '100%', tablet: 'auto' },
                        }}
                    >
                        <Typography variant={'body5'} color={'text_100'}>
                            {t('common.button.cancel')}
                        </Typography>
                    </StyledButton>
                    <StyledButton
                        color={'btn_point_bg'}
                        variant="contained"
                        size="small"
                        disabled={!pfs}
                        onClick={addFolder}
                        sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    >
                        {t('common.action.select')}
                    </StyledButton>
                </Stack>
            </DialogActions>
        </Dialog>
    );
}
