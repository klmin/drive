/** 발급된 API Key 목록 — 커서 기반 조회 + 무한 스크롤. */
import { Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import React, { useEffect, useMemo, useState } from 'react';
import api from '@/utils/api';
import ApiKeyTable, { ADMIN_API_KEY_COLUMNS, HEAD_HEIGHT } from './ApiKeyTable';
import { ROW_HEIGHT } from './ApiKeyRowCells';
import { parseApiKeysFiltersFromParams } from '@/utils/queryString';
import { useRouter } from '@/hooks/useRouter';

/**
 * 관리자 화면은 URL 쿼리로 필터를 받고(syncWithQuery), 설정 화면은 본인 발급 키만 고정 높이로 보여준다.
 * 설정 화면처럼 페이지 안에 끼워 넣을 때는 maxVisibleRows 로 높이를 행 수에 맞춘다.
 */
export default function ApiKeys({
    reloadToken = 0,
    listUrl = 'admins/api-keys',
    listParams,
    basePath,
    columns = ADMIN_API_KEY_COLUMNS,
    syncWithQuery = true,
    maxVisibleRows,
}) {
    const router = useRouter();

    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);
    const [apiKeyList, setApiKeyList] = useState([]);

    const [loading, setLoading] = useState(true);

    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    // 목록을 다시 읽었음을 테이블에 알리는 값
    const [listResetToken, setListResetToken] = useState(0);

    // 목록을 1페이지부터 다시 읽는 대신 바뀐 행만 갱신한다 — 스크롤 위치가 유지된다
    // 단건 조회 API가 없어 서버 값을 다시 읽어올 수 없으므로, 응답으로 결과가 확정되는
    // 액션(활성화/비활성화/만료일 수정)에만 쓴다. 재발급은 목록 필드를 돌려주지 않아 제외.
    const listPatch = useMemo(
        () => ({
            /** 상태가 바뀐 행의 status만 교체 */
            patchStatus: (apiKeySeq, status) => {
                setApiKeyList(prevList =>
                    prevList.map(it => (it.apiKeySeq === apiKeySeq ? { ...it, status } : it))
                );
            },
            /** 만료일이 바뀐 행의 expireDate만 교체 */
            patchExpireDate: (apiKeySeq, expireDate) => {
                setApiKeyList(prevList =>
                    prevList.map(it =>
                        it.apiKeySeq === apiKeySeq
                            ? // 다이얼로그가 과거 날짜를 막으므로 수정 성공 시 만료 상태는 항상 해제된다
                              { ...it, expireDate, isExpired: false }
                            : it
                    )
                );
            },
        }),
        []
    );

    const getListItems = async (isNewSearch = false) => {
        let params = syncWithQuery
            ? parseApiKeysFiltersFromParams(router.query)
            : parseApiKeysFiltersFromParams({ ...listParams });
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                ps: 20,
            };
        }

        try {
            const res = await api.Get(listUrl, { params });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data;

                if (isNewSearch) {
                    setApiKeyList(newData);
                    setListResetToken(prev => prev + 1);
                } else {
                    setApiKeyList(prevList => {
                        return [...prevList, ...newData];
                    });
                }
            }
        } catch (err) {
            console.error('Api Key list fetch failed:', err);
        }
    };

    useEffect(() => {
        setTimeout(() => setLoading(false), 250);
    }, []);

    useEffect(() => {
        if (!cursorId) return;
        if (cursorSearchCount <= 0) return; // 첫 렌더는 건너뛰고, 카운트가 올라갔을 때만 다음 페이지 요청

        getListItems(false);
    }, [cursorSearchCount]);

    useEffect(() => {
        if (!router.isReady) return;
        if (!syncWithQuery) return;
        setCursorId(null);

        getListItems(true);
    }, [router.query]);

    // 쿼리와 무관한 목록은 마운트 시 한 번 읽는다
    useEffect(() => {
        if (syncWithQuery) return;

        getListItems(true);
    }, []);

    // 발급 직후 첫 페이지부터 다시 읽는다
    useEffect(() => {
        if (!reloadToken) return;

        setCursorId(null);
        getListItems(true);
    }, [reloadToken]);

    // 행 경계선(1px)이 높이에 더해져 스크롤이 생기지 않도록 행마다 1px씩 여유를 둔다
    const fixedHeight = maxVisibleRows
        ? HEAD_HEIGHT + Math.max(1, Math.min(apiKeyList.length, maxVisibleRows)) * (ROW_HEIGHT + 1)
        : null;

    return (
        <Box
            sx={
                fixedHeight
                    ? { height: fixedHeight, flexShrink: 0, width: '100%' }
                    : { flex: 1, minHeight: 0, width: '100%' }
            }
        >
            {loading ? (
                <Stack
                    sx={{ width: '100%', height: '88%' }}
                    alignItems="center"
                    justifyContent="center"
                >
                    <Typography variant="body1" color="text_60">
                        Loading...
                    </Typography>
                </Stack>
            ) : (
                <ApiKeyTable
                    data={apiKeyList}
                    reload={() => getListItems(true)}
                    hasNext={hasNext}
                    setCursorSearchCount={setCursorSearchCount}
                    listResetToken={listResetToken}
                    listPatch={listPatch}
                    columns={columns}
                    basePath={basePath}
                />
            )}
        </Box>
    );
}
