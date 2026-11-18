'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Autocomplete, Box, IconButton, Paper, TextField } from '@mui/material';
import Image from 'next/image';
import debounce from 'just-debounce-it';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import palette from '@/theme/palette';
import { filterControlSx } from '@/components/common/input/AdminFilterSelect';
import AuditLogEllipsisText from './AuditLogEllipsisText';
import auditLogTokens from './auditLogTokens';

/** 후보 드롭다운 최대 높이 — 32px 행 기준 12행 남짓 */
const LIST_MAX_HEIGHT = 400;

/** 입력창과 후보 드롭다운 사이 간격 */
const POPUP_OFFSET = 4;

/** 서버가 페이징 없이 전부 내리므로 표시 개수는 화면에서 자른다 */
const MAX_OPTIONS = 50;

const SEARCH_DEBOUNCE_MS = 300;

/**
 * 감사 로그엔 관리자 행위도, 이미 탈퇴한 계정의 기록도 남는다.
 * 검색 API 기본값(USER·ACTIVE)으로는 그런 행위자를 못 찾으므로 범위를 명시해서 넓힌다.
 */
const SEARCH_ROLE_TYPES = 'ADMIN,USER';
const SEARCH_USER_STATUSES = 'ACTIVE,INACTIVE,PENDING_DELETE,DELETED';

/** 시안 표기 — 이영희(younghee.lee@thinkfree.com) */
const optionLabelOf = user => `${user.userName}(${user.userId})`;

/**
 * filterControlSx 위에 얹는 Autocomplete 보정.
 * MUI가 inputRoot·input에 자체 패딩과 endAdornment 자리를 넣어서 시안 패딩을 덮는다.
 */
const autocompleteInputSx = {
    '& .MuiAutocomplete-inputRoot': {
        padding: '4px 12px !important',
        flexWrap: 'nowrap',
    },
    '& .MuiAutocomplete-input': {
        padding: '0 !important',
        minWidth: 0,
    },
    // 지우기 버튼은 흐름 안에 둔다 — MUI 기본 absolute면 값과 겹친다
    '& .MuiAutocomplete-endAdornment': {
        position: 'static',
        display: 'flex',
        flexShrink: 0,
    },
};

/** 지우기(X) 버튼 — 시안 아이콘이 20px 기준이라 여백 없이 20px 자리에 맞춘다 */
const clearButtonSx = {
    p: 0,
    width: 20,
    height: 20,
    flexShrink: 0,
    '&:hover': { backgroundColor: 'transparent' },
};

const listboxSx = {
    maxHeight: LIST_MAX_HEIGHT,
    // 시안 상하 space/1(4). MUI 기본 8px을 덮는다
    py: 1,
    '& .MuiAutocomplete-option': {
        minHeight: 32,
        height: 32,
        // 시안 패딩 — 상하 space/2(8), 좌우 space/4(16)
        px: 4,
        py: 2,
        gap: 2,
        // 키보드 이동(Mui-focused)과 마우스 hover가 같은 배경이다
        '&.Mui-focused, &[aria-selected="true"], &:hover': {
            backgroundColor: `${auditLogTokens.backgroundPaper} !important`,
        },
    },
};

/** 시안 드롭다운 — radius 6 / 테두리 / 메뉴 그림자 */
function UserSearchPaper(props) {
    return (
        <Paper
            {...props}
            elevation={0}
            sx={{
                borderRadius: '6px',
                backgroundColor: palette.white,
                boxShadow: auditLogTokens.menuShadow,
                border: `1px solid ${auditLogTokens.dividerSubtle}`,
            }}
        />
    );
}

/**
 * 감사 로그 행위자 검색 입력.
 * 입력하면 같은 테넌트의 사용자를 찾아 후보를 띄우고, 고르면 그 계정의 userId가 필터값이 된다.
 * 후보에 없는 계정도 조회할 수 있게 직접 입력한 값은 그대로 둔다.
 */
export default function AuditLogUserSearchField({ value, onChange, onSearch, width = 260 }) {
    const t = useTranslations();

    const [options, setOptions] = useState([]);
    const [open, setOpen] = useState(false);

    // 늦게 도착한 이전 검색 응답이 최신 결과를 덮지 않도록 순번으로 거른다
    const requestSeqRef = useRef(0);

    const fetchUsers = useCallback(async keyword => {
        const seq = ++requestSeqRef.current;
        try {
            const res = await api.Get('admins/users/search', {
                params: { sk: keyword, rt: SEARCH_ROLE_TYPES, st: SEARCH_USER_STATUSES },
            });
            if (seq !== requestSeqRef.current) return;

            const users = res?.data?.data ?? [];
            setOptions(users.slice(0, MAX_OPTIONS));
            setOpen(users.length > 0);
        } catch (e) {
            if (seq !== requestSeqRef.current) return;
            console.error(e);
            setOptions([]);
            setOpen(false);
        }
    }, []);

    const debouncedFetchUsers = useMemo(
        () => debounce(keyword => fetchUsers(keyword), SEARCH_DEBOUNCE_MS),
        [fetchUsers]
    );

    useEffect(() => () => debouncedFetchUsers.cancel?.(), [debouncedFetchUsers]);

    /** 후보를 비우고 진행 중인 응답도 버린다 */
    const resetOptions = useCallback(() => {
        debouncedFetchUsers.cancel?.();
        requestSeqRef.current += 1;
        setOptions([]);
        setOpen(false);
    }, [debouncedFetchUsers]);

    const handleInputChange = (event, next, reason) => {
        // 후보 선택·초기화로 생긴 변경은 여기서 다시 검색하지 않는다
        if (reason !== 'input') return;

        onChange(next);

        if (!next.trim()) {
            resetOptions();
            return;
        }
        debouncedFetchUsers(next);
    };

    const handleChange = (event, selected) => {
        // freeSolo라 직접 입력한 문자열이 그대로 올라오기도 한다
        onChange(typeof selected === 'string' ? selected : (selected?.userId ?? ''));
        setOpen(false);
    };

    const handleClear = () => {
        onChange('');
        resetOptions();
    };

    const handleKeyDown = e => {
        // 목록이 열려 있으면 Enter는 후보 선택용이다
        if (e.key !== 'Enter' || open) return;
        onSearch();
    };

    return (
        <Autocomplete
            freeSolo
            open={open}
            onOpen={() => setOpen(options.length > 0)}
            onClose={() => setOpen(false)}
            options={options}
            inputValue={value}
            onInputChange={handleInputChange}
            onChange={handleChange}
            // 선택하면 입력창엔 userId가 남는다 — 그대로 조회 파라미터로 쓰는 값이다
            getOptionLabel={option => (typeof option === 'string' ? option : option.userId)}
            isOptionEqualToValue={(option, selected) => option.userSeq === selected?.userSeq}
            // 서버가 이미 걸러 준 결과다 — 화면에서 다시 거르면 이름·이메일 교차 검색이 깨진다
            filterOptions={x => x}
            slots={{ paper: UserSearchPaper }}
            slotProps={{
                listbox: { sx: listboxSx },
                // 시안 — 입력창과 4px 띄운다. 위로 뒤집혀도 같은 간격이 적용된다
                popper: {
                    modifiers: [{ name: 'offset', options: { offset: [0, POPUP_OFFSET] } }],
                },
            }}
            renderOption={(props, option) => {
                const { key, ...optionProps } = props;
                return (
                    <Box component="li" key={option.userSeq} {...optionProps}>
                        {/* 폭을 넘치면 말줄임되고, 그때만 전체 값이 툴팁으로 뜬다 */}
                        <AuditLogEllipsisText
                            text={optionLabelOf(option)}
                            variant="body5"
                            color={auditLogTokens.textSecondary}
                            sx={{ flex: '1 1 0', minWidth: 0 }}
                        />
                    </Box>
                );
            }}
            renderInput={params => (
                <TextField
                    {...params}
                    size="small"
                    placeholder={t('admin.auditLog.filter.userSearch.placeholder')}
                    onKeyDown={handleKeyDown}
                    sx={{ ...filterControlSx, ...autocompleteInputSx, width }}
                    slotProps={{
                        input: {
                            ...params.InputProps,
                            startAdornment: (
                                <Box sx={{ display: 'flex', flexShrink: 0 }}>
                                    <Image
                                        src="/common/icon-ic-20-search.svg"
                                        alt=""
                                        width={20}
                                        height={20}
                                    />
                                </Box>
                            ),
                            endAdornment: value ? (
                                <IconButton size="small" onClick={handleClear} sx={clearButtonSx}>
                                    <Image
                                        src="/common/icon-ic-20-close.svg"
                                        alt=""
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            ) : null,
                        },
                    }}
                />
            )}
        />
    );
}
