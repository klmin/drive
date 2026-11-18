import { Box, ButtonBase, InputAdornment, MenuItem, Stack, Typography } from '@mui/material';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledSelect from '@/components/common/input/StyledSelect';
import palette from '@/theme/palette';
import { QUOTA_UNITS } from '@/utils/quota';

// ↑/↓ 증감 값 — 1씩, 1 아래로는 내려가지 않음(이미 1 이하면 그대로), 소수 셋째 자리 유지. 바꿀 값이 없으면 null
export const stepQuotaValue = (value, direction) => {
    const current = parseFloat(value);
    const base = isNaN(current) ? 0 : current;
    let next = base + direction;
    if (next < 1) {
        if (base <= 1) return null;
        next = 1;
    }
    return String(Number(next.toFixed(3)));
};

const stepButtonSx = {
    height: '16px',
    width: '20px',
    borderRadius: '2px',
    color: palette.text_60,
    '&:hover': { backgroundColor: palette.btn_white_bg_hover },
    '& .MuiSvgIcon-root': { fontSize: '18px' },
};

// 용량 입력칸 + 단위(GB/MB) 선택 — 숫자만 입력, 형식 판정은 호출부 검증
export default function QuotaInput({
    name,
    value,
    unit,
    onChange,
    onUnitChange,
    error,
    errorMessage,
    placeholder,
    sx,
}) {
    const allowPattern = /^[0-9]*$/;

    // ▲/▼ 버튼·↑/↓ 키 공통
    const step = direction => {
        const next = stepQuotaValue(value, direction);
        if (next !== null) onChange({ target: { name, value: next } });
    };

    return (
        // sx(여백·글자색)는 입력칸·셀렉트를 감싼 줄에 — 입력칸에만 주면 셀렉트와 높이가 어긋난다
        // 셀렉트는 88px 고정, 입력칸이 간격 4px 을 뺀 나머지 폭을 차지
        <Stack direction="row" alignItems="flex-start" gap="4px" sx={{ width: '100%', ...sx }}>
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <StyledTextField
                    name={name}
                    value={value}
                    onChange={e => {
                        if (!allowPattern.test(e.target.value)) return;
                        // 앞자리 0 제거(0555 → 555)
                        onChange({
                            target: { name, value: e.target.value.replace(/^0+(?=\d)/, '') },
                        });
                    }}
                    onKeyDown={e => {
                        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
                        e.preventDefault();
                        step(e.key === 'ArrowUp' ? 1 : -1);
                    }}
                    placeholder={placeholder}
                    variant="outlined"
                    height={40}
                    error={error}
                    errorMessage={errorMessage}
                    fullWidth
                    // ▲/▼ 버튼을 입력칸 오른쪽 끝에 붙인다 — StyledTextField 의 끝 아이콘 여백(12px) 제거
                    sx={{
                        '& .MuiInputBase-root.MuiInputBase-adornedEnd': {
                            paddingInlineEnd: '0 !important',
                        },
                    }}
                    InputProps={{
                        // 키보드는 ↑/↓ 키로 같은 동작 — 버튼은 탭 이동·스크린리더 대상에서 뺀다
                        endAdornment: (
                            <InputAdornment position="end">
                                <Stack aria-hidden>
                                    <ButtonBase
                                        tabIndex={-1}
                                        onMouseDown={e => e.preventDefault()}
                                        onClick={() => step(1)}
                                        sx={stepButtonSx}
                                    >
                                        <KeyboardArrowUpIcon />
                                    </ButtonBase>
                                    <ButtonBase
                                        tabIndex={-1}
                                        onMouseDown={e => e.preventDefault()}
                                        onClick={() => step(-1)}
                                        sx={stepButtonSx}
                                    >
                                        <KeyboardArrowDownIcon />
                                    </ButtonBase>
                                </Stack>
                            </InputAdornment>
                        ),
                    }}
                />
            </Box>
            <Box sx={{ flexShrink: 0 }}>
                <StyledSelect
                    select
                    value={unit}
                    onChange={e => onUnitChange(e.target.value)}
                    height={40}
                    width={88}
                    fullWidth
                >
                    {QUOTA_UNITS.map(u => (
                        <MenuItem key={u} value={u}>
                            <Typography variant="body5">{u}</Typography>
                        </MenuItem>
                    ))}
                </StyledSelect>
            </Box>
        </Stack>
    );
}
