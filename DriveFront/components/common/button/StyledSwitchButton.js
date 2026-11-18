'use client';

import React from 'react';
import { styled } from '@mui/material/styles';
import { Switch, Typography, Stack } from '@mui/material';
import palette from '@/theme/palette';

// const CustomSwitch = styled(Switch)(({ theme }) => ({
//     // width: 42,
//     // height: 26,
//     // padding: 0,
//     // display: 'flex',
//     // '&:active .MuiSwitch-thumb': {
//     //     backgroundColor: 'red',
//     // },

//     '& .MuiSwitch-switchBase': {
//         '&.Mui-checked': {
//             color: '#fff',
//             '& + .MuiSwitch-track': {
//                 backgroundColor: '#3a3d49cc',
//                 opacity: 1,
//                 border: 0,
//             },
//         },
//     },
//     // '& .MuiSwitch-thumb': {
//     //     boxShadow: '0 1px 3px rgba(0,0,0,.3)',
//     //     width: 22,
//     //     height: 22,
//     //     borderRadius: '50%',
//     //     backgroundColor: '#fff',
//     // },
//     '& .MuiSwitch-track': {
//         // borderRadius: 13,
//         backgroundColor: palette.text_40,
//         // opacity: 1,
//         // transition: theme.transitions.create(['background-color'], {
//         //     duration: 200,
//         // }),
//     },
// }));

const CustomSwitch = styled(Switch)({
    width: 40,
    height: 24,
    padding: 0,
    '& .MuiSwitch-switchBase': {
        padding: 3,
        '&.Mui-checked': {
            transform: 'translateX(16px)',
            '& .MuiSwitch-thumb': {
                backgroundColor: palette.btn_white_bg_normal,
                border: `1px solid ${palette.text_80}`,
            },
            '& + .MuiSwitch-track': {
                backgroundColor: palette.text_80,
                opacity: 1,
            },
        },
    },
    '& .MuiSwitch-thumb': {
        width: 18,
        height: 18,
        backgroundColor: palette.btn_white_bg_normal,
        border: `1px solid ${palette.text_40}`,
        boxShadow: '0px 2px 1px -1px rgba(24, 26, 42, 0.16)',
        boxSizing: 'border-box',
    },
    '& .MuiSwitch-track': {
        position: 'absolute',
        width: 26,
        height: 12,
        top: 6,
        left: 7,
        borderRadius: 100,
        backgroundColor: palette.text_40,
        opacity: 1,
    },
    // MUI 기본 비활성 트랙(opacity 0.12)이 거의 안 보여서 색은 그대로 두고 바깥 opacity 로만 흐리게 한다
    '& .MuiSwitch-switchBase.Mui-disabled + .MuiSwitch-track': {
        opacity: 1,
    },
});

export default function StyledSwitchButton({ label, checked, onChange, disabled = false }) {
    return (
        <Stack direction="row" spacing={1} alignItems="center">
            <CustomSwitch
                checked={checked}
                onChange={onChange}
                disabled={disabled}
                sx={disabled ? { opacity: 0.5 } : undefined}
            />
            {label && (
                <Typography fontSize={14} fontWeight={400} color="#4B4D55">
                    {label}
                </Typography>
            )}
        </Stack>
    );
}

export function AdminStyledSwitchButton({ label, checked, onChange }) {
    return (
        <Stack direction="row" spacing={0.5} alignItems="center">
            {label && (
                <Typography variant="body5" color="text_80">
                    {label}
                </Typography>
            )}
            <CustomSwitch checked={checked} onChange={onChange} />
        </Stack>
    );
}
