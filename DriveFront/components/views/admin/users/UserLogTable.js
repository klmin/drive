import {
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Stack,
    Typography,
    Box,
    CircularProgress,
} from '@mui/material';
import Image from 'next/image';
import dayjs from 'dayjs';
import palette from '@/theme/palette';
import { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { styled } from '@mui/material/styles';
import typography from '@/theme/typography';
import { useTranslations } from 'use-intl';

const Root = styled('div')(({ theme }) => ({
    table: {
        boxShadow: 'none',
        width: '100%',
        height: '100%',
    },

    '.table-section-label': {
        padding: '19px 8px 9px',
        ...typography.body6,
        color: palette.text_60,
        [theme.breakpoints.down('md')]: {
            height: 'auto',
            padding: '19px 8px 9px',
            borderBottom: 'none',
        },
    },

    tHead: {
        height: 30,
        borderTop: `1px solid ${palette.divider_8}`,
        borderBottom: `1px solid ${palette.divider_8}`,
        borderRight: 'none',
        backgroundColor: palette.white,
    },

    th: {
        ...typography.body9,
        color: palette.text_60,
        padding: '0px 8px 0px 12px',
        borderBottom: `none !important`,
        borderTop: `1px solid ${palette.divider_8}`,
        [theme.breakpoints.down('md')]: {
            padding: '0px 6px',
        },
    },

    '& thead tr': {
        position: 'sticky',
        top: 0,
        zIndex: 1,
        backgroundColor: 'white',
        boxShadow: `0 3px 5px -2px ${palette.divider_12}`,
    },

    td: {
        height: 44,
        padding: '0px 8px 0px 12px',
        fontSize: 13,
        fontWeight: 'normal',
        color: palette.text_80,
        cursor: 'pointer',
        borderBottom: `1px solid ${palette.divider_8}`,
        [theme.breakpoints.down('md')]: {
            height: 86,
            padding: '8px 6px',
            borderBottom: 'none',
            borderTop: `1px solid ${palette.divider_8}`,
        },
    },
}));

const UserLogTable = ({ userSeq }) => {
    const t = useTranslations();
    const [logs, setLogs] = useState([]);
    const [loadingInit, setLoadingInit] = useState(false); // 초기 로딩
    const [loadingMore, setLoadingMore] = useState(false);

    const tableContainerRef = useRef(null);
    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);
    const [cursorSearchCount, setCursorSearchCount] = useState(0);

    // 간단 캐시: 이 컴포넌트 인스턴스 기준
    // const fetchedRef = useRef(false);
    console.log(userSeq);

    const getLogs = async isNewSearch => {
        if (isNewSearch) setLoadingInit(true);
        else setLoadingMore(true);

        try {
            const res = await api.Get(`admins/users/${userSeq}/log`, {
                params: {
                    ps: isNewSearch || !cursorId ? 20 : 10,
                    ci: isNewSearch ? null : cursorId,
                },
            });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data ?? [];
                if (isNewSearch) {
                    setLogs(newData);
                } else {
                    setLogs(prevList => [...prevList, ...newData]);
                }
            }
        } finally {
            if (isNewSearch) setLoadingInit(false);
            else setLoadingMore(false);
        }
    };

    useEffect(() => {
        setLogs([]);
        setCursorId(null);
        setHasNext(false);
        setCursorSearchCount(0);
        getLogs(true);
    }, [userSeq]);

    const handleTableScroll = useCallback(() => {
        const container = tableContainerRef.current;
        if (container) {
            const { scrollTop, scrollHeight, clientHeight } = container;

            if (scrollHeight - scrollTop - clientHeight < 100 && hasNext && !loadingMore) {
                setCursorSearchCount(prev => prev + 1);
            }
        }
    }, [hasNext, loadingMore]);

    useEffect(() => {
        const container = tableContainerRef.current;
        if (!container) return;

        container.addEventListener('scroll', handleTableScroll);
        return () => container.removeEventListener('scroll', handleTableScroll);
    }, [handleTableScroll]);

    useEffect(() => {
        if (cursorSearchCount <= 0) return;
        if (!hasNext) return;
        if (loadingMore) return;
        getLogs(false);
    }, [cursorSearchCount]);

    if (loadingInit) {
        return (
            <Stack direction={'column'} gap={4} mt={{ md: 0, xs: 6 }}>
                <CircularProgress size={16} />
                <Typography variant="body9">Loading...</Typography>
            </Stack>
        );
    }

    return (
        <Stack direction={'column'} gap={4} mt={{ md: 0, xs: 0 }}>
            <Stack direction={'row'} alignItems={'center'} gap={2}>
                <Box
                    display={'flex'}
                    alignItems={'center'}
                    justifyContent={'center'}
                    sx={{
                        width: 30,
                        height: 30,
                        backgroundColor: palette.table_bg_selected,
                        borderRadius: 30,
                    }}
                >
                    <Image
                        src={'/setting/icon-ic-security.svg'}
                        alt={'security'}
                        width={24}
                        height={24}
                    />
                </Box>
                <Typography variant={'h7'}>{t('Settings.security.title')}</Typography>
            </Stack>
            <Root>
                <TableContainer ref={tableContainerRef} sx={{ maxHeight: 44 * 5 }}>
                    <Table stickyHeader size="small">
                        <TableHead
                            sx={{
                                display: {
                                    xs: 'none',
                                    md: 'table-header-group',
                                },
                            }}
                        >
                            <TableRow>
                                <TableCell className="table-header-cell" sx={{ align: 'center' }}>
                                    {t('Settings.security.location')}
                                </TableCell>
                                <TableCell className="table-header-cell">
                                    {t('Settings.security.timestamp')}
                                </TableCell>
                                <TableCell className="table-header-cell">
                                    {t('Settings.security.eventType.name')}
                                </TableCell>
                            </TableRow>
                        </TableHead>

                        <TableBody>
                            {logs.map((s, index) => (
                                <TableRow key={s.logSeq ?? index}>
                                    {/* 위치 */}
                                    <TableCell sx={{ maxWidth: 580 }}>
                                        <Box display="flex" alignItems="center" gap={2}>
                                            <Image
                                                src="/common/icon-ic-security-list.svg"
                                                alt="location"
                                                width={24}
                                                height={24}
                                            />

                                            <Stack direction="column" gap="2px">
                                                <Typography variant={'body6'} color={'text_80'}>
                                                    {s.region} / {s.ipAddress}
                                                </Typography>

                                                {/* 모바일 표시 */}
                                                <Typography
                                                    variant="body9"
                                                    sx={{ display: { xs: 'block', md: 'none' } }}
                                                >
                                                    {dayjs(s.registerDate).format(
                                                        'YYYY. M. D. H:mm'
                                                    )}
                                                </Typography>

                                                <Typography
                                                    variant="body9"
                                                    sx={{
                                                        display: { xs: 'block', md: 'none' },
                                                        fontWeight: 500,
                                                    }}
                                                    color={getColor(s.activityType)}
                                                >
                                                    {s.activityType}
                                                </Typography>
                                            </Stack>
                                        </Box>
                                    </TableCell>

                                    {/* 시간 */}
                                    <TableCell
                                        sx={{
                                            width: 136,
                                            display: { xs: 'none', md: 'table-cell' },
                                        }}
                                    >
                                        <Typography
                                            variant="body9"
                                            color={'text_60'}
                                            sx={{ fontVariantNumeric: 'tabular-nums' }}
                                        >
                                            {dayjs(s.registerDate).format('YYYY. M. D. H:mm')}
                                        </Typography>
                                    </TableCell>

                                    {/* 이벤트 */}
                                    <TableCell
                                        sx={{
                                            width: 123,
                                            display: { xs: 'none', md: 'table-cell' },
                                            fontWeight: 500,
                                        }}
                                        style={{
                                            color: getColor(s.activityType),
                                        }}
                                    >
                                        {s.activityType}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Root>
        </Stack>
    );
};

export default UserLogTable;

const getColor = type => {
    if (type === 'LOGIN') return palette.text_tab_selected;
    if (type === 'LOGOUT') return palette.status_error;
    return palette.status_confirm;
};
