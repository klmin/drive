import { Box, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { styled } from '@mui/material/styles';
import { useState } from 'react';
import palette from '@/theme/palette';

const StyledToggleGroup = styled(ToggleButtonGroup)(({ theme }) => ({
    backgroundColor: '#ffffff',
    border: '1px solid #DFE0E2',
    height: 30,
    width: '100%',
    '&.Mui-selected:hover': {
        borderRadius: 1,
    },
}));

const StyledToggleButton = styled(ToggleButton)(({ theme }) => ({
    fontSize: 13,
    fontWeight: 500,
    color: palette.text_80,
    border: 'none',
    textTransform: 'none',
    padding: '6px 0',
    '&:not(:first-of-type)': {
        borderLeft: '1px solid #E3E4E6',
    },
}));

// 1개일 때 보여줄 박스
const SingleLabelBox = styled(Box)(({ theme }) => ({
    backgroundColor: '#ffffff',
    border: '1px solid #DFE0E2',
    height: 30,
    fontSize: 13,
    fontWeight: 500,
    color: palette.text_80,
    textAlign: 'center',
    lineHeight: '30px',
    borderRadius: '4px',
}));

export default function StyledToggle({ items, value, onChange, sx }) {
    if (items.length === 1) {
        return (
            <SingleLabelBox
                sx={{
                    ...sx,
                    // width: sx?.width ? sx.width / 2 : '50%', // 전달된 width의 절반
                }}
            >
                {items[0].title}
            </SingleLabelBox>
        );
    }
    return (
        <StyledToggleGroup
            value={value}
            sx={sx}
            exclusive
            onChange={(e, newValue) => newValue && onChange(newValue)}
        >
            {items.map((item, index) => (
                <StyledToggleButton
                    key={index}
                    value={item.value}
                    sx={{
                        fontWeight: item.value === value ? 600 : 400,
                        width: '50%',
                        '&.Mui-selected': {
                            backgroundColor: palette.btn_white_bg_hover,
                            color: palette.text_80,
                            borderRadius: index === 0 ? '50px 0 0 50px' : '0 50px 50px 0',
                            width: '51%',
                        },
                        '&:hover': {
                            borderRadius: index === 0 ? '50px 0  0 50px' : '0 50px 50px 0',
                        },
                    }}
                >
                    {item.title}
                </StyledToggleButton>
            ))}
        </StyledToggleGroup>
    );
}
