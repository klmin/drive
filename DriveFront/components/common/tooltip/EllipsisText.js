import React, { useLayoutEffect, useRef, useState } from 'react';
import { Box, Typography } from '@mui/material';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';

export default function EllipsisText({
    children,
    variant = 'body9',
    color = 'text_80',
    sx,
    boxSx,
}) {
    const textRef = useRef(null);
    const [isEllipsis, setIsEllipsis] = useState(false);

    const measure = () => {
        const el = textRef.current;
        if (!el) return;
        // 소수점/서브픽셀 여유 1px
        setIsEllipsis(el.scrollWidth > el.clientWidth + 1);
    };

    useLayoutEffect(() => {
        const el = textRef.current;
        if (!el) return;

        const raf = requestAnimationFrame(measure);
        const ro = new ResizeObserver(() => measure());
        ro.observe(el);

        return () => {
            cancelAnimationFrame(raf);
            ro.disconnect();
        };
    }, [children]);

    return (
        <BaseTooltip title={children} disableHoverListener={!isEllipsis}>
            {/* 부모가 flex 컨테이너면 이 Box가 줄어들 여지를 만들어줌 */}
            <Box sx={{ minWidth: 0, flex: 1, ...(boxSx || {}) }}>
                <Typography
                    ref={textRef}
                    variant={variant}
                    color={color}
                    noWrap
                    sx={{
                        display: 'block',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        minWidth: 0,
                        ...(sx || {}),
                    }}
                >
                    {children}
                </Typography>
            </Box>
        </BaseTooltip>
    );
}
