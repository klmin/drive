'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from '@/hooks/useRouter';
import Box from '@mui/material/Box';
import { Stack, Typography } from '@mui/material';
import { useTranslations } from 'use-intl';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import { parseShareFolderFiltersFromParams } from '@/utils/queryString';
import ShareFolderTable from '@/components/views/s/ShareFolderTable';
import ShareFolderPath from '@/components/views/s/ShareFolderPath';
import Image from 'next/image';
import { useVvh } from '@/hooks/useVvh';

export default function ShareFolder() {
    const router = useRouter();
    const t = useTranslations();

    // { resourceSeq, resourceName } 배열 — 루트부터 현재 폴더까지
    const [pathStack, setPathStack] = useState([]);

    const [fileList, setFileList] = useState([]);
    const [hasNext, setHasNext] = useState(false);
    const [cursorId, setCursorId] = useState(null);
    const [cursorValue, setCursorValue] = useState(null);
    const [cursorType, setCursorType] = useState(null);
    const [loading, setLoading] = useState(true);
    const [cursorSearchCount, setCursorSearchCount] = useState(0);

    // 최초 진입 시 pfs에 해당하는 폴더명 조회해서 스택 초기화
    useEffect(() => {
        if (!router.isReady || !router.query.pfs) return;
        if (pathStack.length > 0) return;

        getResourceDetail(router.query.pfs, { ss: router.query.ss }).then(data => {
            if (data) {
                setPathStack([{ resourceSeq: data.resourceSeq, resourceName: data.resourceName }]);
            }
        });
    }, [router.isReady, router.query.pfs]);

    const getListItems = useCallback(
        async (isNewSearch = false) => {
            let params = parseShareFolderFiltersFromParams(router.query);
            if (!params.pfs) return;

            if (!isNewSearch) {
                params = { ...params, ci: cursorId, cv: cursorValue, ct: cursorType };
            }

            const res = await api.Get('resources', { params });
            if (res?.status === 200) {
                setHasNext(res.data?.hasNext);
                setCursorId(res.data?.nextCursor);
                setCursorValue(
                    !res.data?.nextCursorValue && router.query.sb === 'size'
                        ? 0
                        : res.data?.nextCursorValue
                );
                setCursorType(res.data?.nextCursorType);
                if (isNewSearch) {
                    setFileList(res.data?.data ?? []);
                } else {
                    setFileList(prev => [...prev, ...(res.data?.data ?? [])]);
                }
            }
        },
        [router.query, cursorId, cursorValue, cursorType]
    );

    useEffect(() => {
        if (!router.isReady || !router.query.pfs) return;
        getListItems(true).finally(() => setLoading(false));
    }, [router.isReady, router.query]);

    // 목록에서 폴더 클릭 → 스택에 push하고 이동
    const handleFolderClick = useCallback(
        item => {
            setPathStack(prev => [
                ...prev,
                { resourceSeq: item.resourceSeq, resourceName: item.resourceName },
            ]);
            router.push({
                pathname: '/s/folder',
                query: { pfs: item.resourceSeq, ss: router.query.ss },
            });
        },
        [router]
    );

    // breadcrumb 클릭 → 해당 위치까지 스택을 slice하고 이동
    const handleBreadcrumbClick = useCallback(
        (item, index) => {
            setPathStack(prev => prev.slice(0, index + 1));
            router.push({
                pathname: '/s/folder',
                query: { pfs: item.resourceSeq, ss: router.query.ss },
            });
        },
        [router]
    );

    useVvh();

    return (
        <Box
            sx={{
                width: '100%',
                height: 'calc(var(--vvh, 1vh) * 100)',
                display: 'flex',
                flexDirection: 'column',
                pt: { xs: 7, md: 8 },
                pb: 16,
                px: { xs: 4, md: 12 },
                overflow: 'hidden',
            }}
        >
            <ShareFolderPath
                img={<Image src={'/menu/icon-ic-share.svg'} alt="folder" fill />}
                pathStack={pathStack}
                onItemClick={handleBreadcrumbClick}
            />

            <Box sx={{ flex: 1, overflow: 'auto', mt: 4 }}>
                {loading ? (
                    <Stack
                        sx={{ width: '100%', height: '100%' }}
                        alignItems="center"
                        justifyContent="center"
                    >
                        <Typography variant="body1" color="text_60">
                            Loading...
                        </Typography>
                    </Stack>
                ) : fileList.length > 0 ? (
                    <ShareFolderTable
                        data={fileList}
                        hasNext={hasNext}
                        cursorSearchCount={cursorSearchCount}
                        setCursorSearchCount={setCursorSearchCount}
                        onFolderClick={handleFolderClick}
                    />
                ) : (
                    <Stack
                        sx={{ width: '100%', height: '80%' }}
                        alignItems="center"
                        justifyContent="center"
                        flexDirection="column"
                    >
                        <Image
                            src={'/drive/img-img-empty.svg'}
                            alt={'empty'}
                            width={120}
                            height={120}
                        />
                        <Typography variant="h3" color="text_80" mt={4}>
                            {t('Shared.text1')}
                        </Typography>
                    </Stack>
                )}
            </Box>
        </Box>
    );
}
