'use client';

import { useState } from 'react';
import { TextField } from '@mui/material';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import ArrowIcon from '@/components/common/icons/ArrowIcon';

/**
 * 관리자 필터 바 입력 공통 스타일 — 높이 32 / radius 6.
 * 셀렉트 말고 같은 줄의 텍스트·날짜 입력도 같은 모양이라 함께 쓴다.
 */
export const filterControlSx = {
    // 고정폭이되 줄(부모)보다 넓어지지 않게
    maxWidth: '100%',
    '& .MuiOutlinedInput-root': {
        height: 32,
        borderRadius: '6px',
        backgroundColor: palette.white,
        // 시안 14/Regular/20 — 지정하지 않으면 테마 body1(500)이 상속된다
        ...typography.body5,
        color: palette.control_text,
        // 시안 패딩 — 상하 space/1(4), 좌우 space/3(12)
        padding: '4px 12px',
        // 폭이 줄면 값이 잘리는 대신 말줄임
        '& .MuiOutlinedInput-input': {
            padding: 0,
            textOverflow: 'ellipsis',
        },
        // 시안 구조: [값] gap 8 [아이콘 16]. 아이콘을 흐름 안에 두고 gap으로 띄운다
        gap: '8px',
        '& .MuiSelect-select': {
            padding: '0 !important',
            minWidth: 0,
            flex: '1 1 0',
        },
        // 아이콘은 pointer-events가 없어 클릭이 root로 떨어진다 — 아래 onClick이 셀렉트로 넘긴다
        '& .MuiSelect-icon': {
            position: 'static',
            flexShrink: 0,
            fontSize: 16,
            color: palette.control_text_hint,
        },
        '& fieldset': { borderColor: palette.border_subtle },
        // MUI 기본 hover는 테두리를 text.primary로 진하게 바꾼다 — 평상시 색으로 눌러 둔다
        '&:hover:not(.Mui-focused) .MuiOutlinedInput-notchedOutline': {
            borderColor: palette.border_subtle,
        },
        '&.Mui-focused fieldset': { borderWidth: 1, borderColor: palette.control_text_hint },
    },
    '& input::placeholder': { color: palette.text_muted, opacity: 1 },
};

/**
 * 관리자 필터 바용 셀렉트. 감사 로그 필터(이벤트·채널·결과)에서 쓰는 컴포넌트다.
 * 옵션은 children으로 MenuItem을 넘기고, 다중 선택 등은 slotProps로 TextField에 그대로 전달한다.
 *
 * sx는 얕게 병합되므로 filterControlSx와 같은 선택자를 주면 덮어쓴다.
 */
export default function AdminFilterSelect({ width = 140, sx, slotProps, children, ...props }) {
    // MUI가 클릭을 받는 건 값 영역뿐이라 패딩·화살표 자리는 반응하지 않는다.
    // 열림 상태를 직접 들고 입력창 아무 곳이나 누르면 열리게 한다.
    const [open, setOpen] = useState(false);

    return (
        <TextField
            select
            size="small"
            slotProps={{
                ...slotProps,
                input: {
                    ...slotProps?.input,
                    onMouseDown: () => {
                        if (!props.disabled) setOpen(true);
                    },
                },
                select: {
                    IconComponent: ArrowIcon,
                    open,
                    onOpen: () => setOpen(true),
                    onClose: () => setOpen(false),
                    ...slotProps?.select,
                },
            }}
            sx={{
                ...filterControlSx,
                width,
                '& .MuiOutlinedInput-root': {
                    ...filterControlSx['& .MuiOutlinedInput-root'],
                    cursor: 'pointer',
                },
                ...sx,
            }}
            {...props}
        >
            {children}
        </TextField>
    );
}
