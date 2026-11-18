import React from 'react';
import { Autocomplete, Typography } from '@mui/material';
import { CountryCodes } from '@/constants/COUNTRY_CODES';
import StyledTextField from './StyledTextField';
import typography from '@/theme/typography';

export default function CountrySelect({ countryCode, setCountryCode, handleChange }) {
    return (
        <Autocomplete
            options={CountryCodes}
            getOptionLabel={option => `${option.name} (${option.value})`}
            isOptionEqualToValue={(option, value) =>
                option.value === value.value && option.name === value.name
            }
            disableClearable
            value={countryCode}
            onChange={(e, newValue) => {
                if (!newValue) return;

                if (setCountryCode) {
                    setCountryCode(newValue); // 선택된 국가 코드 상태 저장
                }

                if (handleChange) {
                    handleChange({
                        target: {
                            name: 'countryCode',
                            value: newValue,
                        },
                    });
                }
            }}
            noOptionsText={
                <Typography
                    sx={{
                        fontWeight: 400,
                        color: 'text.primary',
                        ...typography.body5,
                    }}
                >
                    No Options
                </Typography>
            }
            renderInput={params => (
                <StyledTextField
                    {...params}
                    variant="outlined"
                    sx={{
                        ...(handleChange && { height: 40 }),
                        minWidth: 180,
                        backgroundColor: '#f5f5f5',
                        '& fieldset': { border: 'none' },
                    }}
                    InputProps={{
                        ...params.InputProps,
                        sx: {
                            fontWeight: 400,
                            color: 'text.primary',
                            ...typography.body5, // 옵션 리스트와 동일한 스타일 적용

                            // Autocomplete에서 화살표 위치가 포커스 전/후 바뀌는 문제를 방지하기 위한 override
                            '& .MuiAutocomplete-inputRoot': {
                                paddingBlock: '5.55px !important', // 포커스 전/후 패딩 값 동일하게 맞춤
                            },
                            '& .MuiAutocomplete-inputRoot.Mui-focused': {
                                paddingBlock: '5.55px !important', // 포커스 시 변경되던 값 override (4.55px -> 5.55px)
                            },
                            '& .MuiAutocomplete-endAdornment': {
                                right: '0.9375rem !important', // 포커스 전/후 동일하게
                            },
                        },
                    }}
                />
            )}
            renderOption={(props, option) => (
                <li {...props} key={`${option.name}-${option.value}`}>
                    <Typography
                        sx={{
                            fontWeight: 400,
                            color: 'text.primary',
                            ...typography.body5,
                        }}
                    >
                        {option.name} ({option.value})
                    </Typography>
                </li>
            )}
            slotProps={{
                popupIndicator: { title: '' },
                clearIndicator: { title: '' },
            }}
        />
    );
}
