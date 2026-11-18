import React, { useState, useEffect, useMemo } from 'react';
import { MenuItem, Typography, Box, Stack, Popover, Divider } from '@mui/material';
import { styled } from '@mui/material/styles';
import palette from '@/theme/palette';
import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import { useTranslations } from 'next-intl';
import StyledDatePicker from '@/components/common/input/StyledDatePicker';
import StyledButton from '@/components/common/button/StyledButton';
import dayjs from 'dayjs';
import { ArrowFilledDown } from '@/icon/Arrow';
import { buildFilterQuery } from '@/utils/queryString';
import { useRouter } from '@/hooks/useRouter';
import ProfileImage from '@/components/common/input/ProfileImage';
import debounce from 'just-debounce-it';
import api from '@/utils/api';
import useClientConfig from '@/hooks/useClientConfig';

// 필터 버튼 스타일
const FilterButton = styled(StyledButton, {
    shouldForwardProp: prop => prop !== 'hasValue',
})(({ theme, hasValue }) => ({
    borderRadius: 25,
    fontSize: 14,
    fontWeight: 400,
    height: 28,
    backgroundColor: hasValue ? palette.table_bg_selected : 'transparent',
    display: 'flex',
    alignItems: 'center',
    gap: '3px',
    padding: '5px 8px 5px 12px',
    transition: 'background-color 0.2s ease',
    border: hasValue ? `1px solid ${palette.table_divider_selected}` : 'none',
    '& .MuiOutlinedInput-notchedOutline': {
        border: 'none',
    },
    '&:hover': {
        backgroundColor: palette.btn_white_bg_hover,
    },
    '&:active': {
        backgroundColor: palette.btn_white_bg_selected,
    },
    '&:focus-visible': {
        backgroundColor: palette.btn_white_bg_selected,
    },
}));

// 메뉴 아이템 스타일
const CustomMenuItem = styled(MenuItem)({
    fontSize: 14,
    height: 36,
    minWidth: 195,
    padding: '0 12px',
});

export default function FilterDropdown({
    open,
    setOpen,
    filterList,
    placeholder,
    value,
    setValue,
    type,
}) {
    const t = useTranslations();
    const router = useRouter();
    const { intellectEnabled } = useClientConfig();

    const [anchorEl, setAnchorEl] = useState(null);

    const [showDatePicker, setShowDatePicker] = useState(false);
    const [startDate, setStartDate] = useState(null);
    const [endDate, setEndDate] = useState(null);
    const [search, setSearch] = useState('');
    const [filteredList, setFilteredList] = useState([]);

    const isOwnerFilter = type ? type === 'owner' || type === 'deleteUser' : false;
    const listToRender = isOwnerFilter ? (search ? filteredList : filterList) : filterList;

    const handleOpen = event => {
        setAnchorEl(event.currentTarget);
        setOpen(true);
    };

    const handleClose = () => {
        setAnchorEl(null);
        setOpen(false);
    };

    const handleSearchChange = e => {
        const value = e.target.value;
        setSearch(value);

        // owner 아닐 경우 filterList 그대로 사용
        if (!isOwnerFilter) return;

        // 검색어 없을 때
        if (!value.trim()) {
            setFilteredList([]);
            debouncedFetchUsers.cancel?.();
            return;
        }

        // 검색어 있을 때
        debouncedFetchUsers(value);
    };

    const fetchFilteredUsers = async keyword => {
        try {
            const res = await api.Get('users/search', {
                params: {
                    sk: keyword,
                },
            });
            setFilteredList(
                res?.data?.data?.map(u => {
                    return {
                        name: u?.userName,
                        value: u?.userId,
                        src: api.getProfileImage(u?.userSeq),
                        alt: 'user',
                        ...u,
                    };
                })
            );
        } catch (e) {
            console.error(e);
            setFilteredList([]);
        }
    };

    const debouncedFetchUsers = useMemo(
        () =>
            debounce(keyword => {
                fetchFilteredUsers(keyword);
            }, 300),
        []
    );

    // 값 선택 핸들러
    // labelValue 같이 전송. 새로고침 시 해당 유저 Id 필터 값 표시용
    const handleSelect = (selectedValue, labelValue = '') => {
        setValue(selectedValue);
        handleClose();
        console.log('selectedValue', selectedValue);

        const root = router.pathname;
        const queryParams = buildFilterQuery(
            router.query,
            type,
            selectedValue?.value,
            labelValue || ''
        );
        router.push({ pathname: root, query: queryParams }, undefined, { shallow: true });
    };

    useEffect(() => {
        if (!open) {
            setSearch('');
            setShowDatePicker(false);
            setStartDate(null);
            setEndDate(null);
        }
    }, [open]);

    useEffect(() => {
        return () => {
            debouncedFetchUsers.cancel?.();
        };
    }, [debouncedFetchUsers]);

    const renderDisplayValue = () => {
        if (isOwnerFilter) {
            return placeholder;
        }

        const selectedItem = filterList?.find(item => {
            // 날짜인 경우 value가 배열이므로 별도 비교
            if (Array.isArray(item.value) && Array.isArray(value?.value)) {
                console.log('comparing arrays', item.value, value.value);
                console.log(
                    'comparison result',
                    item.value[0] === value.value[0],
                    item.value[1] === value.value[1]
                );
                return item.value[0] === value.value[0] && item.value[1] === value.value[1];
            }
            return item.value === value?.value;
        });

        if (selectedItem) {
            return t(selectedItem.name);
        }
        if (Array.isArray(value?.value) && value.value.length > 0) {
            return value.value.join(' ~ ');
        }
        return placeholder;
    };

    return (
        <>
            <FilterButton hasValue={!!value} onClick={handleOpen}>
                <Typography
                    variant="body5"
                    noWrap
                    sx={{ color: !!value ? palette.text_tab_selected : palette.text_80 }}
                >
                    {renderDisplayValue()}
                </Typography>
                <ArrowFilledDown
                    opacity={!!value ? '.8' : '.6'}
                    fill={!!value ? '#4566ea' : '#3a3d4999'}
                />
            </FilterButton>

            {/** 필터 - 사용자 검색 */}
            <Popover
                open={open}
                anchorEl={anchorEl}
                onClose={handleClose}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'left',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'left',
                }}
                PaperProps={{
                    sx: {
                        borderRadius: 1,
                        mt: 1,
                        py: 2,
                        maxHeight: 350,
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        border: `1px solid ${palette.control_border_normal}`,
                    },
                }}
            >
                {isOwnerFilter && (
                    <>
                        <Box px={2}>
                            <StyledTextField
                                variant={'standard'}
                                placeholder={t('ownerFilter.inputBase')}
                                value={search}
                                sx={{
                                    '& .MuiInputBase-root': {
                                        px: '0px !important',
                                        color: palette.text_40,
                                        fontSize: 14,
                                        height: 36,
                                    },
                                }}
                                onChange={handleSearchChange}
                                InputProps={{
                                    startAdornment: (
                                        <Image
                                            src="/common/icon-ic-search.svg"
                                            width={20}
                                            height={20}
                                            alt="search"
                                        />
                                    ),
                                }}
                            />
                        </Box>
                        <Divider
                            style={{
                                margin: '4px 0',
                                borderColor: palette.divider_8,
                                borderBottomWidth: 0.5,
                            }}
                        />
                    </>
                )}

                {listToRender
                    ?.filter(item => !(!intellectEnabled && item.value === 'note'))
                    .map((item, index) => (
                        <CustomMenuItem
                            sx={{
                                height: isOwnerFilter ? 50 : 36,
                                gap: 2.5,
                                background: value === item && palette.btn_white_bg_selected,
                            }}
                            key={index}
                            onClick={() => handleSelect(item, item?.userName)}
                        >
                            <Stack direction="row" alignItems="center" gap={2.5}>
                                {item?.src && (
                                    <ProfileImage
                                        src={item.src}
                                        alt={`${item.alt}-${index}`}
                                        width={20}
                                        height={20}
                                    />
                                )}
                                <Stack direction="column" gap={0.5}>
                                    <Typography
                                        variant={isOwnerFilter ? 'body7' : 'body5'}
                                        color={'text_80'}
                                    >
                                        {item?.name?.includes('.') ? t(item.name) : item.name}
                                    </Typography>
                                    {item?.userId && (
                                        <Typography variant="body9" color="text_40">
                                            {item.userId}
                                        </Typography>
                                    )}
                                </Stack>
                            </Stack>
                        </CustomMenuItem>
                    ))}

                {type === 'date' && (
                    <Box>
                        <StyledButton
                            variant={'text'}
                            sx={{
                                width: '100%',
                                justifyContent: 'flex-start',
                                height: 36,
                                px: 3,
                                py: 0,
                            }}
                            onClick={() => setShowDatePicker(prev => !prev)}
                        >
                            <Typography variant={'body5'} color={'text_80'}>
                                {t('dateFilter.custom')}
                            </Typography>
                        </StyledButton>
                    </Box>
                )}

                {showDatePicker && (
                    <Box sx={{ py: 2, px: 3 }}>
                        <Stack direction={'column'} justifyContent={'center'} gap={2}>
                            <Typography variant={'body7'} color={'text_80'}>
                                {t('common.dateFilter.inputBefore')}
                            </Typography>
                            <StyledDatePicker
                                value={startDate}
                                setValue={setStartDate}
                                maxDate={endDate || undefined}
                            />
                        </Stack>
                        <Stack direction={'column'} justifyContent={'center'} gap={2} mt={2}>
                            <Typography variant={'body7'} color={'text_80'}>
                                {t('common.dateFilter.inputAfter')}
                            </Typography>
                            <StyledDatePicker
                                value={endDate}
                                setValue={setEndDate}
                                minDate={startDate || undefined}
                            />
                        </Stack>
                    </Box>
                )}

                <Divider
                    style={{
                        margin: '4px 0',
                        borderColor: palette.divider_8,
                        borderBottomWidth: 0.5,
                    }}
                />

                <CustomMenuItem
                    // sx={{borderTop: `1px solid ${palette.divider_8}`, height: 36}}
                    onClick={() => !showDatePicker && handleSelect(undefined)}
                >
                    <Stack
                        sx={{ width: '100%' }}
                        direction={'row'}
                        alignItems={'center'}
                        justifyContent={'space-between'}
                    >
                        <Box
                            display="flex"
                            alignItems="center"
                            gap={1}
                            onClick={() => showDatePicker && handleSelect(undefined)}
                        >
                            <Image
                                src="/common/icon-ic-refresh_active.svg"
                                width={20}
                                height={20}
                                alt="clear"
                            />
                            <Typography variant="body5" color="text_tab_selected">
                                {t('filter.clear')}
                            </Typography>
                        </Box>
                        {showDatePicker && (
                            <StyledButton
                                color={'btn_point_bg'}
                                variant="contained"
                                onClick={() => {
                                    handleSelect({
                                        value: [
                                            dayjs(startDate).format('YYYY-MM-DD'),
                                            dayjs(endDate).format('YYYY-MM-DD'),
                                        ],
                                    });
                                }}
                                sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                                disabled={!startDate || !endDate}
                            >
                                <Typography variant={'body9'} color={'white'}>
                                    {t('common.button.save')}
                                </Typography>
                            </StyledButton>
                        )}
                    </Stack>
                </CustomMenuItem>
            </Popover>
        </>
    );
}
