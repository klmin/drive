'use client';

import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Box,
    Typography,
    Menu,
    MenuItem,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    TableContainer,
    Stack,
} from '@mui/material';
import Image from 'next/image';
import React, { useEffect, useRef, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import { getFile } from '@/constants/FILES';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import FolderCreateDialog from '@/components/common/dialog/FolderCreateDialog';
import checkExists from '@/utils/checkExists';
import { useRouter } from '@/hooks/useRouter';
import DialogPath from '@/components/views/DialogPath';
import dayjs from 'dayjs';
import { getParentFolderInfoList } from '@/apis/resourceApi';
import { useUser } from '@/context/UserContext';
import { useResources } from '@/provider/ResourceProvider';
import { hasShareResource, isMyDriveOwnedItem } from '@/utils/resourceUtils';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import { getLanguage } from '@/utils/authTokens';

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

const EllipsisTableText = ({ text, variant = 'body9' }) => {
    const textRef = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        if (!textRef.current) return;
        const el = textRef.current;
        setIsTruncated(el.scrollWidth > el.clientWidth);
    }, [text]);

    const content = (
        <Box
            sx={{
                minWidth: 0,
                overflow: 'hidden',
            }}
        >
            <Typography
                ref={textRef}
                variant={variant}
                color="text_80"
                noWrap
                sx={{
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }}
            >
                {text}
            </Typography>
        </Box>
    );

    return isTruncated ? <BaseTooltip title={text}>{content}</BaseTooltip> : content;
};

export default function FileMoveDialog({ open, onClose, ...props }) {
    const { items, reload, setCheckItems, listPatch } = props;
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();

    const { showSnackbar } = useSnackbarContext();
    const [anchorEl, setAnchorEl] = useState(null);
    const [selectedDrive, setSelectedDrive] = useState(
        router.pathname === '/shared' ? 'SHARE' : 'MY'
    );
    const [pfs, setPfs] = useState(undefined);
    const [ss, setSs] = useState(undefined);
    const [path, setPath] = useState([]);
    const [folderList, setFolderList] = useState([]);
    const [folderDialogOpen, setFolderDialogOpen] = useState(false);
    const [updateSuccess, setUpdateSuccess] = useState(false);
    const { openDialog } = useDialog();
    /** 드라이브 선택 변경 여부 — true면 뒤늦게 도착한 초기 경로 조회 결과를 반영하지 않는다 */
    const driveChangedRef = useRef(false);

    const shouldReloadLeftTree = items?.some(i => i.resourceType === 'FOLDER');
    /** 이동 중인 폴더 — 자기 자신·하위로는 이동할 수 없어 목록에서 진입을 막는다 */
    const movingFolderSeqs = new Set(
        (items || []).filter(i => i.resourceType === 'FOLDER').map(i => i.resourceSeq)
    );
    const { getResources } = useResources(); // 폴더가 이동된 경우 좌측 폴더 트리 reload 필요

    const cookieLang = getLanguage('USER');
    const isKo = cookieLang === 'ko';

    const disabledMove = () => {
        const selectedDriveLocation = selectedDrive === 'MY' ? 'MY' : 'SHARE';

        // 공유 문서함 루트로는 이동할수 없음
        if (selectedDriveLocation === 'SHARE' && path.length === 0) return true;

        // 이동 중인 폴더 자신 또는 그 하위로는 이동할 수 없음
        if (path.some(p => movingFolderSeqs.has(p.resourceSeq))) return true;

        // 동일 경로일때는 이동할 수 없음
        if (items[0].resourceLocation !== selectedDriveLocation) return false;
        if (items[0].parentFolderSeq === null && path.length === 0) return true;
        if (path.length === 0) return false;
        if (items[0].parentFolderSeq === path[path.length - 1].resourceSeq) return true;

        return false;
    };

    const handleMenuOpen = e => setAnchorEl(e.currentTarget);
    const handleMenuClose = drive => {
        if (drive) {
            driveChangedRef.current = true;
            setSelectedDrive(drive);
        }
        setPfs('');
        setSs('');
        setPath([]);
        setAnchorEl(null);
    };

    const handleMoveItem = async () => {
        // console.log('path', path);
        // console.log('targetFolder', path[path.length - 1]);
        const targetFolder = path[path.length - 1];
        /** 중복 검사할 대상 드라이브의 userSeq — 내 드라이브는 항상 로그인 사용자 */
        const targetSs =
            selectedDrive === 'MY'
                ? user?.userSeq
                : targetFolder?.sharedByUserSeq || targetFolder?.userSeq || user?.userSeq;
        let successCount = 0;
        // 성공한 항목만 목록에 반영하기 위해 따로 모은다
        const movedItems = [];
        let failCount = 0;
        let lockedCount = 0;
        let quotaExceededCount = 0;

        // 이동 하려는 리소스가 공유 리소스인 지 (공유 문서함 루트 제외)
        // 공유 문서함 -> isShared && router.query.pfs && router.query.ss
        // 중요 문서함 -> !isShared && resourceLocation === 'SHARE' && fullPath == null
        const isShared = router.asPath.includes('shared');
        let isMovingFolderShared = false;
        if ((isShared && router.query.pfs && router.query.ss) || hasShareResource(items)) {
            if (isMyDriveOwnedItem(targetFolder, user)) {
                isMovingFolderShared = true;
            }
        }

        // 공유 문서함 하위 리소스 선택 -> '내 드라이브' 선택 후 이동 -> 공유 해제 경고 다이얼로그 표시
        if (isMovingFolderShared) {
            const userConfirmed = await openDialog('alert', {
                title: t('shareRemoveDialog.text1'),
                description: t('shareRemoveDialog.text2'),
                buttonName: t('common.action.move'),
            });

            if (!userConfirmed) {
                return;
            }
        }

        try {
            for (const i of items) {
                console.log('i', i);
                const moveFunc = async () => {
                    const res = await api.Post(
                        i?.resourceType === 'FILE'
                            ? `files/${i.resourceSeq}/move`
                            : `folders/${i.resourceSeq}/move`,
                        {
                            ts:
                                targetFolder?.sharedByUserSeq ||
                                targetFolder?.userSeq ||
                                user?.userSeq,
                            ss: !targetFolder
                                ? i?.userSeq
                                : i?.sharedByUserSeq || i?.userSeq || undefined,
                            pfs: targetFolder?.resourceSeq,
                        }
                    );
                    if (res?.code === 200) {
                        successCount++;
                        movedItems.push(i);
                    } else if (res?.status === 423) {
                        failCount++;
                        lockedCount++;
                    } else if (res?.status === 400 && res?.errorCode === 'QUOTA_003') {
                        quotaExceededCount++;
                        failCount++;
                    } else {
                        failCount++;
                    }
                };

                const exists = await checkExists(
                    {
                        resourceName: i.resourceName,
                        resourceType: i.resourceType,
                        ss: targetSs,
                    },
                    pfs
                );

                if (exists?.resourceSeq) {
                    const userConfirmed = await openDialog('alert', {
                        title: t('duplicateDialog.text1', {
                            resourceName: i.resourceName,
                            resourceType: i.resourceType,
                            ss: ss,
                        }),
                        description: t('duplicateDialog.text2'),
                        buttonName: t('common.button.upload'),
                    });

                    if (userConfirmed) {
                        await moveFunc();
                    }
                } else if (exists) {
                    await moveFunc();
                } else {
                    failCount++;
                }
            }

            // 모든 루프 끝난 후 결과 토스트 한 번만 출력
            if (successCount > 0 && failCount === 0) {
                showSnackbar(t('move.success'), 'success');
                if (shouldReloadLeftTree) {
                    await getResources();
                }
            } else if (successCount > 0 && failCount > 0) {
                showSnackbar(
                    t('move.partial', {
                        success: successCount,
                        fail: failCount,
                        locked: lockedCount,
                        quota: quotaExceededCount,
                    }),
                    'warning'
                ); // 일부 성공/실패
                if (shouldReloadLeftTree) {
                    await getResources();
                }
            } else {
                if (lockedCount > 0) {
                    showSnackbar(t('lock.locked'), 'error');
                } else if (quotaExceededCount > 0) {
                    showSnackbar(t('move.fail.quotaExceeded'), 'error');
                } else {
                    showSnackbar(t('move.fail.general'), 'error');
                }
            }
        } catch (err) {
            console.error('파일이동 실패:', err);
        } finally {
            onClose();
            // 목록 전체 재조회 대신 이동된 행만 반영한다(스크롤 위치 유지)
            if (listPatch?.move) await listPatch.move(movedItems);
            else reload(true);
            setCheckItems([]);
        }
    };

    const getResourceList = async () => {
        try {
            const res =
                selectedDrive === 'MY'
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

    const getParentFolder = async () => {
        try {
            const parentFolderList = await getParentFolderInfoList(
                items[0]?.parentFolderSeq || '',
                items[0]?.sharedByUserSeq || items[0]?.userSeq
            );
            // 조회 중 대상 드라이브가 바뀌었으면 초기 경로로 되돌리지 않는다
            if (driveChangedRef.current) return;

            const lastIdx = parentFolderList.length - 1;

            setPath(parentFolderList);
            setPfs(parentFolderList[lastIdx]?.resourceSeq || '');
            setSs(parentFolderList[lastIdx]?.ss || '');
            setFolderList(parentFolderList);
        } catch (err) {
            console.error('부모 폴더 가져오기 실패: ', err);
        }
    };

    // 초기 상단 폴더 경로 세팅
    useEffect(() => {
        getParentFolder();
    }, []);

    useEffect(() => {
        if (pfs === undefined) return;

        getResourceList();
    }, [selectedDrive, pfs]);

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
                <Stack direction={'row'} spacing={2} sx={{ minWidth: 0 }}>
                    {!isKo && (
                        <Typography
                            variant={'body1'}
                            color={'text_100'}
                            sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
                        >
                            {items?.length === 1
                                ? t('common.action.move')
                                : t('moveDialog.subTitleMultiple', {
                                      count: items?.length,
                                  })}
                        </Typography>
                    )}
                    {items?.length === 1 && (
                        <EllipsisTableText
                            text={items?.[0]?.resourceName}
                            variant={'body1'}
                            color={'text_100'}
                            sx={{ minWidth: 0 }} // 남은 영역만 차지
                        />
                    )}
                    {isKo && (
                        <Typography
                            variant={'body1'}
                            color={'text_100'}
                            sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
                        >
                            {items?.length === 1
                                ? t('common.action.move')
                                : t('moveDialog.subTitleMultiple', {
                                      count: items?.length,
                                  })}
                        </Typography>
                    )}
                </Stack>
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
                        sx={{
                            height: 32,
                            fontSize: 14,
                            px: 3,
                            mr: 1.5,
                        }}
                    >
                        <Typography variant={'body7'} color={'text_80'}>
                            {selectedDrive === 'MY'
                                ? t('common.label.myDrive')
                                : t('common.label.shared')}
                        </Typography>
                    </StyledButton>
                    <DialogPath resources={path} setPfs={setPfs} setPath={setPath} />
                </Box>
                <Menu
                    anchorEl={anchorEl}
                    open={Boolean(anchorEl)}
                    onClose={() => setAnchorEl(null)}
                    PaperProps={{
                        sx: {
                            mt: 1,
                            minWidth: 125,
                            boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        },
                    }}
                >
                    <MenuItem onClick={() => handleMenuClose('MY')}>
                        {t('common.label.myDrive')}
                    </MenuItem>
                    <MenuItem onClick={() => handleMenuClose('SHARED')}>
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
                                                aria-disabled={movingFolderSeqs.has(
                                                    folder?.resourceSeq
                                                )}
                                                onClick={() => {
                                                    if (movingFolderSeqs.has(folder?.resourceSeq))
                                                        return;
                                                    setPfs(folder?.resourceSeq);
                                                    setSs(
                                                        folder?.sharedByUserSeq ||
                                                            folder?.userSeq ||
                                                            undefined
                                                    );
                                                    setPath([...path, folder]);
                                                }}
                                                sx={
                                                    movingFolderSeqs.has(folder?.resourceSeq)
                                                        ? {
                                                              opacity: 0.4,
                                                              '& td': { cursor: 'default' },
                                                          }
                                                        : undefined
                                                }
                                            >
                                                <TableCell
                                                    sx={{
                                                        maxWidth: 300,
                                                        cursor: movingFolderSeqs.has(
                                                            folder?.resourceSeq
                                                        )
                                                            ? 'default'
                                                            : 'pointer',
                                                    }}
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
                                                                <EllipsisTableText
                                                                    text={folder.resourceName}
                                                                    variant="body9"
                                                                />
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
                    direction={{
                        xs: 'column',
                        tablet: 'row',
                    }}
                    sx={{ width: '100%' }}
                    spacing={1}
                    justifyContent={'space-between'}
                >
                    <StyledButton
                        onClick={() => setFolderDialogOpen(!folderDialogOpen)}
                        color={'btn_border'}
                        variant="outlined"
                        sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                        size="small"
                    >
                        <Typography variant={'body5'} color={'text_100'}>
                            {t('uploadDialog.newFolder')}
                        </Typography>
                    </StyledButton>
                    <Stack
                        direction={{
                            xs: 'column',
                            tablet: 'row',
                        }}
                        spacing={1}
                    >
                        <StyledButton
                            onClick={onClose}
                            color={'btn_border'}
                            variant="outlined"
                            size="small"
                            sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                        >
                            <Typography variant={'body5'} color={'text_100'}>
                                {t('common.button.cancel')}
                            </Typography>
                        </StyledButton>
                        <StyledButton
                            color={'btn_point_bg'}
                            variant="contained"
                            size="small"
                            onClick={handleMoveItem}
                            sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                            disabled={disabledMove()}
                        >
                            {t('common.action.move')}
                        </StyledButton>
                    </Stack>
                </Stack>
            </DialogActions>
            <FolderCreateDialog
                open={folderDialogOpen}
                onClose={() => setFolderDialogOpen(false)}
                pfs={pfs}
                ss={ss}
                reloadResources={getResourceList} // 다이얼로그 내 목록 reload
                reloadLibraries={reload} // 문서함 목록 reload
            />
        </Dialog>
    );
}
