'use client';

import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { IconButton, InputAdornment, TextField } from '@mui/material';
import Image from 'next/image';
import { useState } from 'react';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import checkDevice from '@/utils/etc';
import { DesktopDatePicker, MobileDatePicker } from '@mui/x-date-pickers';
import { useLocale } from 'next-intl';
import { pickerLocaleTextOf } from '@/constants/dateFormats';

const CustomStyledDateTextField = styled(TextField, {
    shouldForwardProp: prop => prop !== 'fieldHeight' && prop !== 'fieldRadius',
})(({ theme, fieldHeight = 36, fieldRadius = 6 }) => ({
    '& .MuiInputBase-root': {
        height: fieldHeight,
        borderRadius: fieldRadius,
        backgroundColor: 'transparent',
        color: palette.text_80,
        fontSize: typography.body3.fontSize,
        border: `1px solid ${palette.control_border_normal}`,
        paddingRight: 8,

        '&:hover': {
            border: `1px solid ${palette.control_border_hover}`,
        },
        '&.Mui-focused': {
            border: `1px solid ${palette.control_border_selected}`,
            boxShadow: '0 0 0 1px rgba(58, 61, 73, 0.2)',
        },
        '&.Mui-error': {
            borderColor: palette.status_error,
        },
        '&.Mui-disabled': {
            backgroundColor: palette.divider_8,
        },
    },

    '& .MuiOutlinedInput-notchedOutline': {
        border: 'none',
    },

    '& .MuiInputBase-input': {
        padding: '6px 12px',
    },

    '& .MuiInputAdornment-root': {
        marginRight: 4,
    },
}));

export default function StyledDatePicker({
    value,
    setValue,
    minDate,
    maxDate,
    fullWidth = false,
    height = 36,
    borderRadius = 6,
    iconSize = 16,
    error = false,
}) {
    const device = checkDevice();
    const isPhone = device?.isPhone;
    const [open, setOpen] = useState(false);
    // 달력 언어는 앱 언어(driveLanguage_<role> → IntlProvider locale)를 따른다
    const locale = useLocale();

    // 태블릿도 pc와 동일한 스타일로
    const PickerComponent = isPhone ? MobileDatePicker : DesktopDatePicker;

    return (
        <LocalizationProvider
            dateAdapter={AdapterDayjs}
            adapterLocale={locale}
            localeText={pickerLocaleTextOf(locale)}
        >
            <PickerComponent
                value={value}
                onChange={newValue => setValue(newValue)}
                minDate={minDate}
                maxDate={maxDate}
                open={open}
                onOpen={() => setOpen(true)}
                onClose={() => setOpen(false)}
                format="YYYY-MM-DD"
                enableAccessibleFieldDOMStructure={false}
                slotProps={{
                    textField: {
                        size: 'small',
                        error,
                        fullWidth,
                        fieldHeight: height,
                        fieldRadius: borderRadius,
                        InputProps: {
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton sx={{ px: 0 }} onClick={() => setOpen(true)}>
                                        <Image
                                            src="/common/icon-ic-date-range.svg"
                                            width={iconSize}
                                            height={iconSize}
                                            alt="calendar"
                                        />
                                    </IconButton>
                                </InputAdornment>
                            ),
                            readOnly: true,
                        },
                    },
                }}
                slots={{
                    textField: CustomStyledDateTextField,
                }}
            />
        </LocalizationProvider>
    );
}
