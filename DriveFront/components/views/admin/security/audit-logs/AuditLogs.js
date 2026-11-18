/**
 * 감사 로그 조회 화면. GET /api/v1/admins/audit-logs(Drive → 거버넌스 프록시) 결과를 보여준다.
 *
 * 조회 조건은 URL 쿼리가 단일 소스다. 필터 입력은 화면 상태로 들고 있다가 "조회"에서 URL에 반영하고,
 * URL이 바뀌면(조회·페이지 이동·새로고침·뒤로가기) 그 값으로 목록을 부른다.
 */
import React, { useEffect, useState } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { Stack } from '@mui/material';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { buildAuditLogQuery, parseAuditLogFilters } from '@/utils/queryString';
import { buildAuditLogApiParams, EVENT_CATEGORIES, typesOfCategory } from './auditLogFormat';
import AuditLogFilterBar from './AuditLogFilterBar';
import AuditLogTable from './AuditLogTable';

const PAGE_SIZE = 10;

// 빈 쿼리를 파싱한 값이 곧 초기 필터 — 필드 목록을 한 군데서만 관리한다
const INITIAL_FILTERS = parseAuditLogFilters({});

export default function AuditLogs() {
    const router = useRouter();
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [filters, setFilters] = useState(INITIAL_FILTERS);
    const [items, setItems] = useState([]);
    const [pageIndex, setPageIndex] = useState(1);
    const [totalCount, setTotalCount] = useState(null);
    const [loading, setLoading] = useState(false);

    const handleChange = (key, value) => setFilters(prev => ({ ...prev, [key]: value }));

    // 카테고리를 켜면 하위 개별 선택은 걷어낸다 — 이미 전체를 뜻해서 중복된다
    const handleToggleEventCategory = category => {
        setFilters(prev => {
            const on = prev.eventCategories.includes(category);
            const types = typesOfCategory(category);
            return {
                ...prev,
                eventCategories: on
                    ? prev.eventCategories.filter(item => item !== category)
                    : [...prev.eventCategories, category],
                eventTypes: on
                    ? prev.eventTypes
                    : prev.eventTypes.filter(type => !types.includes(type)),
            };
        });
    };

    // 전체 선택 — 모든 카테고리를 켠 상태와 같다. 하나라도 빠졌으면 전부 켜고, 다 켜져 있으면 비운다
    const handleToggleAllEvents = () => {
        setFilters(prev => ({
            ...prev,
            eventCategories:
                prev.eventCategories.length === EVENT_CATEGORIES.length
                    ? []
                    : [...EVENT_CATEGORIES],
            eventTypes: [],
        }));
    };

    const buildParams = (pi, currentFilters) => ({
        ...buildAuditLogApiParams(currentFilters),
        pi,
        ps: PAGE_SIZE,
    });

    const handleFetchFail = () => {
        setItems([]);
        showSnackbar(t('admin.auditLog.toast.fetchFail'), 'error');
    };

    const fetchLogs = async (pi = 1, currentFilters = filters) => {
        setLoading(true);
        try {
            const res = await api.Get('admins/audit-logs', {
                params: buildParams(pi, currentFilters),
            });
            if (res?.status === 200 && Array.isArray(res?.data?.data)) {
                setItems(res.data.data);
                setPageIndex(res.data.pageIndex ?? pi);
                setTotalCount(res.data.totalCount ?? null);
            } else {
                // 감사 로그 비활성 응답 포함
                handleFetchFail();
            }
        } catch (err) {
            handleFetchFail();
        } finally {
            setLoading(false);
        }
    };

    const applyQuery = (nextFilters, pi) =>
        router.push(
            { pathname: router.pathname, query: buildAuditLogQuery(nextFilters, pi) },
            undefined,
            { shallow: true }
        );

    useEffect(() => {
        if (!router.isReady) return;

        const next = parseAuditLogFilters(router.query);
        setFilters(next);
        fetchLogs(Number(router.query.pi) || 1, next);
    }, [router.isReady, router.query]);

    const handleReset = () => applyQuery(INITIAL_FILTERS, 1);

    return (
        // 목록 카드가 "남는 높이"를 알 수 있게 이 묶음이 화면의 남은 공간을 차지한다.
        // 페이지가 스크롤하지 않으므로 minHeight는 0 — 남은 높이가 모자라면 카드가 줄어든다.
        <Stack gap={3} sx={{ width: '100%', minWidth: 0, minHeight: 0, flex: 1 }}>
            <AuditLogFilterBar
                filters={filters}
                onChange={handleChange}
                onToggleEventCategory={handleToggleEventCategory}
                onToggleAllEvents={handleToggleAllEvents}
                onSearch={() => applyQuery(filters, 1)}
                onReset={handleReset}
                loading={loading}
            />

            <AuditLogTable
                items={items}
                pageIndex={pageIndex}
                pageSize={PAGE_SIZE}
                totalCount={totalCount}
                loading={loading}
                onMovePage={pi => applyQuery(parseAuditLogFilters(router.query), pi)}
            />
        </Stack>
    );
}
