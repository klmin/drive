'use client';

import { forwardRef } from 'react';
import { Box } from '@mui/material';
import { styled } from '@mui/material/styles';
import TextField from '@mui/material/TextField';
import palette from '@/theme/palette';

const TextFieldStyled = styled(TextField, {
    shouldForwardProp: prop => prop !== 'height',
})(({ height }) => {
    return {
        '& .MuiInputBase-root': {
            height: height || '100%',
            backgroundColor: `${palette.btn_white_bg_normal} !important`,
            border: `1px solid ${palette.control_border_normal}`,
            borderRadius: '4px',

            '& .MuiOutlinedInput-notchedOutline': {
                border: 'none',
            },

            '&:hover': {
                borderColor: palette.control_border_hover,
            },

            '&.Mui-focused': {
                borderColor: palette.control_border_selected,
                borderWidth: '1px',
            },

            '&.Mui-disabled': {
                backgroundColor: `${palette.btn_white_bg_hover} !important`,
                borderColor: palette.control_border_normal,
                '& .MuiSelect-select': {
                    color: palette.text_40,
                    WebkitTextFillColor: palette.text_40,
                },
                '& .MuiSvgIcon-root': {
                    opacity: 0.4,
                },
            },

            '&:before, &:after': {
                display: 'none',
            },
        },

        '& .MuiSelect-select': {
            fontSize: '15px',
            fontWeight: 400,
            lineHeight: '22px',
            color: palette.text_80,
            minHeight: 'unset !important',
            paddingLeft: '0px !important',
            paddingInlineEnd: '24px !important',
            display: 'flex',
            alignItems: 'center',
        },

        '& .MuiSelect-icon': {
            right: '8px',
            color: palette.text_60,
        },

        '& .MuiInputBase-sizeSmall': {
            padding: '0 8px 0 12px',
            borderRadius: '4px',
        },

        // underline variant
        '&.underline .MuiInput-root': {
            borderBottom: `1px solid ${palette.control_border_normal}`,
            backgroundColor: 'transparent !important',
            borderRadius: 0,
            '&:hover': {
                borderBottom: `1px solid ${palette.control_border_hover}`,
            },
            '&.Mui-focused': {
                borderBottom: `2px solid ${palette.control_border_selected}`,
            },
        },
    };
});

const DEFAULT_MENU_PROPS = {
    slotProps: {
        paper: {
            sx: {
                mt: '4px',
                borderRadius: '4px',
                boxShadow: `0px 1px 10px ${palette.control_border_normal}`,
                outline: `1px ${palette.control_border_normal} solid`,
                outlineOffset: '-1px',
                py: '8px',
                px: '1px',
                '& .MuiMenuItem-root': {
                    height: '40px',
                    pl: '12px',
                    pr: '8px',
                    fontSize: '15px',
                    fontWeight: 400,
                    lineHeight: '22px',
                    color: palette.text_80,
                    backgroundColor: `${palette.white} !important`,
                    '&:hover': {
                        backgroundColor: `${palette.btn_white_bg_hover} !important`,
                    },
                    '&.Mui-selected': {
                        backgroundColor: `${palette.btn_white_bg_selected} !important`,
                        '&:hover': {
                            backgroundColor: `${palette.btn_white_bg_selected} !important`,
                        },
                    },
                },
                '& .MuiDivider-root': {
                    borderColor: palette.divider_8,
                    my: '4px',
                },
            },
        },
    },
    MenuListProps: {
        sx: { padding: 0 },
    },
};

const StyledSelect = forwardRef(({ width, height, SelectProps, ...props }, ref) => {
    const { size = 'small', slotProps, variant, ...rest } = props;

    const mergedSelectProps = {
        ...SelectProps,
        MenuProps: {
            ...DEFAULT_MENU_PROPS,
            ...SelectProps?.MenuProps,
            slotProps: {
                paper: {
                    sx: {
                        ...DEFAULT_MENU_PROPS.slotProps.paper.sx,
                        ...SelectProps?.MenuProps?.slotProps?.paper?.sx,
                    },
                },
            },
        },
    };

    return (
        <Box sx={{ width: width || '100%' }}>
            <TextFieldStyled
                size={size}
                inputRef={ref}
                width={width}
                height={height}
                {...rest}
                variant={variant || 'outlined'}
                SelectProps={mergedSelectProps}
                slotProps={{
                    ...slotProps,
                    inputLabel: { ...slotProps?.inputLabel, shrink: true },
                }}
            />
        </Box>
    );
});

StyledSelect.displayName = 'StyledSelect';

export default StyledSelect;
