'use client';

// React Imports
import { forwardRef } from 'react';

// MUI Imports
import { styled } from '@mui/material/styles';
import TextField from '@mui/material/TextField';
import palette from '@/theme/palette';
import { Box, Typography } from '@mui/material';

const TextFieldStyled = styled(TextField, {
    shouldForwardProp: prop => prop !== 'height',
})(({ theme, height, variant }) => {
    const targetHeight = typeof height === 'number' ? `${height}px` : height;

    return {
        '& .MuiInputBase-root': {
            height: height || '100%',
            padding: '0 0 0 12px',
            '.MuiOutlinedInput-notchedOutline': {
                borderColor: palette.control_border_normal,
            },
            '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: palette.control_border_hover,
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: palette.control_border_selected,
                borderWidth: '1px',
            },
            '&.Mui-error .MuiOutlinedInput-notchedOutline': {
                borderColor: '#f86c5e',
            },
            backgroundColor: `${palette.btn_white_bg_normal} !important`,
            border: `1px solid var(--mui-palette-customColors-btn_gray_bg_normal)`,
            '&:not(.Mui-focused):not(.Mui-disabled):not(.Mui-error):hover': {
                // borderColor: 'red',
            },
            '&:active': {
                // borderColor: 'red',
            },
            '&:before, &:after': {
                display: 'none',
            },
            '&.MuiInputBase-sizeSmall': {
                // borderRadius: 'var(--mui-shape-borderRadius)'
                borderRadius: '4px',
            },
            '&.Mui-error': {
                borderColor: 'var(--mui-palette-error-main)',
                outline: '4px solid rgba(248, 108, 94, 0.10)',
                outlineOffset: '0px',
            },
            '&.Mui-focused': {
                outline: '4px solid rgba(58, 61, 73, 0.10)',
                outlineOffset: '0px',
                // borderWidth: 2,
                // borderColor: 'red !important',
                '& .MuiInputBase-input:not(.MuiInputBase-readOnly):not([readonly])::placeholder': {
                    // transform: 'translateX(4px)',
                },
                '& :not(textarea).MuiFilledInput-input': {
                    padding: '6.25px 13px',
                },
                '&:not(.Mui-error).MuiInputBase-colorPrimary': {
                    borderColor: '#3a3d49cc',
                },
                '&.MuiInputBase-colorSecondary': {
                    borderColor: 'var(--mui-palette-secondary-main)',
                },
                '&.MuiInputBase-colorInfo': {
                    borderColor: 'var(--mui-palette-info-main)',
                },
                '&.MuiInputBase-colorSuccess': {
                    borderColor: 'var(--mui-palette-success-main)',
                },
                '&.MuiInputBase-colorWarning': {
                    borderColor: 'var(--mui-palette-warning-main)',
                },
                '&.MuiInputBase-colorError': {
                    borderColor: 'var(--mui-palette-error-main)',
                },
                '&.Mui-error': {
                    borderColor: 'var(--mui-palette-error-main)',
                    outline: '4px solid rgba(248, 108, 94, 0.10)',
                    outlineOffset: '0px',
                },
            },
            '&.Mui-disabled': {
                opacity: 0.4,
            },
        },

        // Adornments
        '& .MuiInputAdornment-root': {
            marginBlockStart: '0px !important',
            '&.MuiInputAdornment-positionStart + .MuiInputBase-input:not(textarea)': {
                // paddingInlineStart: '0px !important',
            },
        },
        '& .MuiInputAdornment-positionEnd': {
            marginLeft: 0,
        },
        '& .MuiInputBase-inputAdornedEnd.MuiInputBase-input': {
            // paddingInlineEnd: '0px !important',
        },

        '& .MuiInputBase-sizeSmall.MuiInputBase-adornedStart.Mui-focused': {
            paddingInlineStart: '12px',
            '& .MuiInputBase-input': {
                // paddingInlineStart: '0px !important',
            },
        },
        '& .MuiInputBase-sizeSmall.MuiInputBase-adornedStart:not(.MuiAutocomplete-inputRoot)': {
            paddingInlineStart: '12px',
        },
        '& .MuiInputBase-sizeSmall.MuiInputBase-adornedEnd:not(.MuiAutocomplete-inputRoot)': {
            paddingInlineEnd: '12px',
        },
        '& .MuiInputBase-sizeSmall.MuiInputBase-adornedEnd.Mui-focused:not(.MuiAutocomplete-inputRoot)':
            {
                paddingInlineEnd: '12px',
                '& .MuiInputBase-input': {
                    paddingInlineEnd: '0px !important',
                },
            },
        '& :not(.MuiInputBase-sizeSmall).MuiInputBase-adornedStart.Mui-focused': {
            paddingInlineStart: '15px',
            '& .MuiInputBase-input': {
                paddingInlineStart: '0px !important',
            },
        },
        '& :not(.MuiInputBase-sizeSmall).MuiInputBase-adornedStart': {
            paddingInlineStart: '16px',
        },
        '& :not(.MuiInputBase-sizeSmall).MuiInputBase-adornedEnd.Mui-focused': {
            paddingInlineEnd: '15px',
            '& .MuiInputBase-input': {
                paddingInlineEnd: '0px !important',
            },
        },
        '& :not(.MuiInputBase-sizeSmall).MuiInputBase-adornedEnd': {
            paddingInlineEnd: '16px',
        },
        '& .MuiInputAdornment-sizeMedium': {
            'i, svg': {
                fontSize: '1.25rem',
            },
        },

        '& .MuiInputBase-input': {
            padding: '0 !important',
            fontSize: '15px',
            fontWeight: 400,
            lineHeight: '22px',
            color: palette.text_80,
            '&:not(textarea).MuiInputBase-inputSizeSmall': {
                padding: '0 16px',
                ...(targetHeight ? { height: '100%' } : { minHeight: targetHeight }),
                backgroundColor: 'transparent',
                ...(variant === 'filled' && {
                    backgroundColor: '#3a3d4905',
                    backdropFilter: 'blur(10px)',
                }),
            },
            '&::placeholder': {
                color: palette.text_40,
                opacity: 1,
            },
        },
        '& :not(.MuiInputBase-sizeSmall).MuiInputBase-root': {
            borderRadius: '8px',
            fontSize: '17px',
            lineHeight: '1.41',
            '& .MuiInputBase-input': {
                padding: '10.8px 16px',
            },
            '&.Mui-focused': {
                '& .MuiInputBase-input': {
                    padding: '9.8px 15px',
                },
            },
        },
        '& .MuiFormHelperText-root': {
            lineHeight: 1.154,
            margin: theme.spacing(1, 0, 0),
            fontSize: theme.typography.body2.fontSize,
            '&.Mui-error': {
                color: 'var(--mui-palette-error-main)',
            },
            '&.Mui-disabled': {
                color: 'var(--mui-palette-text-disabled)',
            },
        },

        // For Select
        '& .MuiSelect-select.MuiInputBase-inputSizeSmall, & .MuiNativeSelect-select.MuiInputBase-inputSizeSmall':
            {
                '& ~ i, & ~ svg': {
                    inlineSize: '20px',
                    blockSize: '25px',
                },
            },
        '& .MuiSelect-select': {
            fontSize: 14,
            color: palette.text_80,
            minHeight: 'unset !important',
            lineHeight: '1.4375em',
            '&.MuiInputBase-input': {
                paddingInlineEnd: '32px !important',
                // backgroundColor: 'red',
            },
        },
        '& .Mui-focused .MuiSelect-select': {
            // backgroundColor: 'red',
            '& ~ i, & ~ svg': {
                // right: '0.9375rem',
            },
        },
        // palette.btn_white_bg_hover
        '& .MuiSelect-select:focus, & .MuiNativeSelect-select:focus': {
            // backgroundColor: 'red',
        },

        // For Autocomplete
        '& :not(.MuiInputBase-sizeSmall).MuiAutocomplete-inputRoot': {
            paddingBlock: '5.55px',
            '& .MuiAutocomplete-input': {
                paddingInline: '8px !important',
                paddingBlock: '5.25px !important',
            },
            '&.Mui-focused .MuiAutocomplete-input': {
                paddingInlineStart: '7px !important',
            },
            '&.Mui-focused': {
                paddingBlock: '4.55px !important',
            },
            '& .MuiAutocomplete-endAdornment': {
                top: 'calc(50% - 12px)',
            },
        },
        '& .MuiAutocomplete-inputRoot.MuiInputBase-sizeSmall': {
            paddingBlock: '4.75px !important',
            paddingInlineStart: '10px',
            '&.Mui-focused': {
                paddingBlock: '3.75px !important',
                paddingInlineStart: '9px',
                '.MuiAutocomplete-input': {
                    paddingBlock: '2.5px',
                    paddingInline: '3px !important',
                },
            },
            '& .MuiAutocomplete-input': {
                paddingInline: '3px !important',
            },
        },
        '& .MuiAutocomplete-inputRoot': {
            display: 'flex',
            gap: '0.25rem',
            '& .MuiAutocomplete-tag': {
                margin: 0,
            },
        },
        '& .MuiAutocomplete-inputRoot.Mui-focused .MuiAutocomplete-endAdornment': {
            right: '.9375rem',
        },

        // For Textarea
        '& .MuiInputBase-multiline': {
            '&.MuiInputBase-sizeSmall': {
                padding: '8px 12px 0px 12px',
                '&.Mui-focused': {
                    padding: '5px 13px',
                },
            },
            '& textarea.MuiInputBase-inputSizeSmall:placeholder-shown': {
                overflowX: 'hidden',
            },
        },
    };
});

/** 입력 아래 빨간 느낌표 + 오류 문구 — 입력칸 외 항목(체크박스 그룹 등)도 같은 모양으로 쓴다 */
export function FieldErrorMessage({ message }) {
    return (
        <Box mt={2} gap={1} display="flex" alignItems="center">
            <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                style={{ flexShrink: 0 }}
            >
                <mask id="mg00tyknfa" maskUnits="userSpaceOnUse" x="1" y="1" width="14" height="14">
                    <circle cx="8" cy="8" r="6.4" fill="#D9D9D9" />
                </mask>
                <g mask="url(#mg00tyknfa)">
                    <path fill="#F86C5E" d="M1.018 1.018h13.964v13.964H1.018z" />
                </g>
                <path
                    d="M7.678 4.544a.797.797 0 0 1 1.116.802l-.318 3.496a.478.478 0 0 1-.951 0l-.319-3.496a.797.797 0 0 1 .472-.802zM8.762 10.762a.761.761 0 1 1-1.523 0 .761.761 0 0 1 1.523 0z"
                    fill="#fff"
                />
            </svg>

            <Typography variant="body7" color={'status_error'}>
                {message}
            </Typography>
        </Box>
    );
}

const StyledTextField = forwardRef(({ width, height, errorMessage, ...props }, ref) => {
    const { size = 'small', slotProps, variant, ...rest } = props;

    return (
        <Box sx={{ width: width || '100%' }}>
            <TextFieldStyled
                size={size}
                inputRef={ref}
                width={width}
                height={height}
                {...rest}
                variant={variant || 'filled'}
                slotProps={{
                    ...slotProps,
                    inputLabel: { ...slotProps?.inputLabel, shrink: true },
                }}
            />
            {errorMessage && <FieldErrorMessage message={errorMessage} />}
        </Box>
    );
});

StyledTextField.displayName = 'CustomTextField';

export default StyledTextField;
