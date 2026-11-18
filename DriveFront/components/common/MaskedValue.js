import { Typography } from '@mui/material';
import { isMaskedValue } from '@/utils/masking';

/** 숨긴 값 자리에 블러로 깔아 두는 글자 — 실제 값과 무관한 고정 문구 */
const DEFAULT_DUMMY = 'xxxxxxxxxx@xxxxxxxx.xxx';

/** 서버가 숨긴 값(__MASKED__)이면 가짜 글자를 블러로, 아니면 값을 그대로 표시 */
export default function MaskedValue({
    value,
    dummy = DEFAULT_DUMMY,
    variant = 'body5',
    color = 'text_60',
    sx,
}) {
    if (!isMaskedValue(value)) {
        return (
            <Typography variant={variant} color={color} sx={sx}>
                {value}
            </Typography>
        );
    }

    // 가짜 글자라 보조기기에는 읽히지 않게 둔다
    return (
        <Typography
            variant={variant}
            color={color}
            aria-hidden
            sx={{ filter: 'blur(5px)', userSelect: 'none', pointerEvents: 'none', ...sx }}
        >
            {dummy}
        </Typography>
    );
}
