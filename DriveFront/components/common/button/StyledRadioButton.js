import { Radio } from '@mui/material';
import { styled } from '@mui/material/styles';
import React from 'react';
import palette from '@/theme/palette';

const CustomRadio = styled(props => <Radio disableRipple {...props} />)(({ theme }) => ({
    padding: 2,
    color: palette.control_border_normal,
    '&:hover': {
        backgroundColor: 'transparent',
    },
    '&.Mui-checked': {
        color: palette.control_border_selected,
    },
    '& .MuiSvgIcon-root': {
        fontSize: 20,
    },
    // '& .MuiRadio-root': {
    //     color: palette.btn_white_bg_normal,
    // },
}));

export default function StyledRadioButton({ value, checked, onChange, disabled, ...props }) {
    return (
        <CustomRadio
            value={value}
            checked={checked}
            onChange={onChange}
            disabled={disabled}
            {...props}
        />
    );
}
