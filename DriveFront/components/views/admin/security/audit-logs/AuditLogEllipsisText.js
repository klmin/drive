'use client';

import React, { useCallback, useRef, useState } from 'react';
import { Typography } from '@mui/material';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import auditLogTokens from './auditLogTokens';

const ellipsisSx = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };

const clampSx = {
    display: '-webkit-box',
    WebkitLineClamp: 2,
    WebkitBoxOrient: 'vertical',
    overflow: 'hidden',
    overflowWrap: 'break-word',
};

// 감사 로그 화면 전용 툴팁 시안 — 전역 MuiTooltip(반투명 배경)과 달리 불투명 배경 + 그림자를 쓴다
const auditTooltipSx = {
    backgroundColor: `${auditLogTokens.buttonSecondaryBg} !important`,
    opacity: '1 !important',
    boxShadow: '0px 2px 4px rgba(0, 0, 0, 0.15)',
    fontWeight: 400,
};

/**
 * 말줄임된 감사 로그 텍스트. 실제로 잘린 경우에만 툴팁으로 전체 값을 보여준다.
 * 판정은 hover 시점에 한다 — 마운트 직후엔 열 폭·웹폰트가 안 잡혀 잘못 측정된다.
 */
export default function AuditLogEllipsisText({ text, variant, color, lines = 1, sx }) {
    const textRef = useRef(null);
    const [truncated, setTruncated] = useState(false);

    const measure = useCallback(() => {
        const el = textRef.current;
        if (!el) return;
        setTruncated(
            lines > 1 ? el.scrollHeight > el.clientHeight : el.scrollWidth > el.clientWidth
        );
    }, [lines]);

    return (
        <BaseTooltip title={truncated ? text : ''} slotProps={{ tooltip: { sx: auditTooltipSx } }}>
            <Typography
                ref={textRef}
                variant={variant}
                onMouseEnter={measure}
                sx={{ color, ...(lines > 1 ? clampSx : ellipsisSx), ...sx }}
            >
                {text}
            </Typography>
        </BaseTooltip>
    );
}
