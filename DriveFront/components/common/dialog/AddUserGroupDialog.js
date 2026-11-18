// components/dialog/AddUserGroupDialog.js
'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    Box,
    ClickAwayListener,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    IconButton,
    List,
    ListItem,
    ListItemText,
    Paper,
    Popper,
    Stack,
    Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import debounce from 'just-debounce-it';
import ProfileImage from '@/components/common/input/ProfileImage';

export default function AddUserGroupDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { selectedUserList, setSelectedUserList, email, setEmail, item } = props;
    const [inviteUserList, setInviteUserList] = useState([]);
    const [filteredUsers, setFilteredUsers] = useState([]);
    const [searchValue, setSearchValue] = useState('');
    const [sendEmail, setSendEmail] = useState(true);
    const [anchorEl, setAnchorEl] = useState(null);
    const fieldRef = useRef(null);

    const handleSearchChange = event => {
        const value = event.target.value;
        setSearchValue(value);
        setAnchorEl(event.currentTarget.closest('.MuiFormControl-root'));

        if (!value.trim()) {
            setFilteredUsers([]);
            debouncedFetchUsers.cancel?.();
            return;
        }
        debouncedFetchUsers(value);
    };

    const handleSelectUser = user => {
        setInviteUserList(prev => {
            const exists = prev.some(u => u.userSeq === user.userSeq); // 또는 다른 고유 값
            if (exists) return prev;

            return [...prev, { ...user }];
        });

        setSearchValue('');
        setAnchorEl(null);
    };

    const handleRemoveUser = item => {
        console.log('inviteUserList before remove:', inviteUserList);
        setInviteUserList(prev => prev.filter(user => user.userName !== item.userName));
    };

    const handleClickAway = () => {
        setAnchorEl(null);
    };

    const fetchFilteredUsers = async value => {
        try {
            const res = item?.shareSeq
                ? await api.Get(`shares/${item?.shareSeq}/users/search`, {
                      params: {
                          ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          sk: value,
                      },
                  })
                : await api.Get(`shares/users/search`, {
                      params: {
                          ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          sk: value,
                      },
                  });
            if (res?.data?.data) {
                setFilteredUsers(res?.data?.data?.items || []);
            }
        } catch (err) {
            console.error('err:', err);
        }
    };

    const debouncedFetchUsers = useMemo(
        () => debounce(value => fetchFilteredUsers(value), 300),
        []
    );

    useEffect(() => {
        return () => {
            debouncedFetchUsers.cancel?.();
        };
    }, [debouncedFetchUsers]);

    useEffect(() => {
        if (selectedUserList?.length > 0) {
            setInviteUserList(selectedUserList);
        }
    }, [selectedUserList]);

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '560px !important',
                    width: '100%',
                    overflow: 'visible',
                },
            }}
        >
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1">{t('common.title.addUser')}</Typography>
                <IconButton onClick={onClose}>
                    <Image src={'/common/icon-ic-close.svg'} width={24} height={24} alt={'close'} />
                </IconButton>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Typography variant={'body5'} color={'text_80'}>
                    {t('shareAddUserDialog.search')}
                </Typography>
                <Stack direction={'row'} alignItems={'center'} gap={2} mt={2.5} ref={fieldRef}>
                    <StyledTextField
                        fullWidth
                        variant="outlined"
                        placeholder="Search Users..."
                        height={40}
                        value={searchValue}
                        onChange={handleSearchChange}
                    />
                    <IconButton
                        sx={{
                            borderRadius: '4px',
                            border: `1px solid ${palette.btn_border}`,
                            width: 40,
                            height: 40,
                        }}
                    >
                        <Image
                            src={'/common/icon-ic-search-gray.svg'}
                            width={24}
                            height={24}
                            alt={'search'}
                        />
                    </IconButton>
                </Stack>
                <Popper
                    open={Boolean(anchorEl) && searchValue}
                    anchorEl={anchorEl}
                    placement="bottom-start"
                    // style={{ zIndex: 1300 }}
                    disablePortal // 포탈 끄고 다이얼로그 내부에 렌더
                    modifiers={[
                        // 검색 창과 동일한 너비로 세팅
                        { name: 'offset', options: { offset: [0, 6] } },
                        {
                            name: 'sameWidth',
                            enabled: true,
                            phase: 'beforeWrite',
                            requires: ['computeStyles'],
                            fn: ({ state }) => {
                                state.styles.popper.width = `${state.rects.reference.width}px`;
                            },
                        },
                    ]}
                    sx={{ zIndex: 1 }}
                >
                    <ClickAwayListener onClickAway={handleClickAway}>
                        <Paper
                            style={{
                                borderBottom: 'none',
                                boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                maxHeight: 200,
                                overflow: 'auto',
                            }}
                        >
                            {filteredUsers?.map(user => (
                                <ListItem
                                    key={`${user.userName}-${user.userEmail}`}
                                    button={'true'}
                                    onClick={() => handleSelectUser(user)}
                                    sx={{
                                        borderBottom: `1px solid ${palette.btn_white_bg_hover}`,
                                        py: '12px',
                                    }}
                                >
                                    <Stack
                                        sx={{ width: '100%', cursor: 'pointer' }}
                                        direction={'row'}
                                        alignItems={'center'}
                                        justifyContent={'space-between'}
                                    >
                                        <Stack direction={'row'} alignItems={'center'} gap={3}>
                                            <ProfileImage
                                                src={api.getProfileImage(user.userSeq)}
                                                width={24}
                                                height={24}
                                                alt={'user'}
                                            />
                                            <Stack>
                                                <Typography variant={'body5'} color={'text_80'}>
                                                    {/** 추후 작업 필요 isExternalTenant */}
                                                    {user.userName}
                                                </Typography>
                                                <Typography variant={'body5'} color={'text_40'}>
                                                    {user.userId}
                                                </Typography>
                                            </Stack>
                                        </Stack>
                                    </Stack>
                                </ListItem>
                            ))}
                        </Paper>
                    </ClickAwayListener>
                </Popper>

                <List
                    sx={{
                        mt: 2,
                        border: `1px solid ${palette.control_border_normal}`,
                        borderRadius: 1,
                        minHeight: 220,
                    }}
                >
                    {inviteUserList.length === 0 ? (
                        <ListItem>
                            <ListItemText
                                primary={t('shareAddUserDialog.invitedUsersLabel')}
                                primaryTypographyProps={{
                                    sx: {
                                        ...typography.body5,
                                        color: palette.text_40,
                                    },
                                }}
                            />
                        </ListItem>
                    ) : (
                        inviteUserList.map(user => (
                            <ListItem key={user.userSeq}>
                                <Stack
                                    sx={{ width: '100%' }}
                                    direction={'row'}
                                    alignItems={'center'}
                                    justifyContent={'space-between'}
                                >
                                    <Stack direction={'row'} alignItems={'center'} gap={3}>
                                        <ProfileImage
                                            src={api.getProfileImage(user.userSeq)}
                                            width={24}
                                            height={24}
                                            alt={'user'}
                                        />
                                        <Stack>
                                            <Typography variant={'body5'} color={'text_80'}>
                                                {/**추후 작업 필요 isExternalTenant */}
                                                {user.userName}
                                            </Typography>
                                            <Typography variant={'body5'} color={'text_40'}>
                                                {user.userId}
                                            </Typography>
                                        </Stack>
                                    </Stack>
                                    <IconButton onClick={() => handleRemoveUser(user)}>
                                        <Image
                                            src={'/common/icon-ic-close-gray.svg'}
                                            width={16}
                                            height={16}
                                            alt={'close'}
                                        />
                                    </IconButton>
                                </Stack>
                            </ListItem>
                        ))
                    )}
                </List>

                <FormControlLabel
                    control={
                        <StyledCheckbox
                            checked={email}
                            onChange={e => setEmail(e.target.checked)}
                        />
                    }
                    label={
                        <Typography variant={'body5'} color={'text_80'}>
                            {t('shareAddUserDialog.sendEmail')}
                        </Typography>
                    }
                    sx={{ mt: 2, pl: 1.8, gap: 2 }}
                />
            </DialogContent>
            <DialogActions>
                <StyledButton
                    onClick={() => {
                        // '취소' 하면 검색 키워드 및 setInviteUserList 초기 값으로 세팅
                        setSearchValue('');
                        setInviteUserList(selectedUserList);
                        onClose();
                    }}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    size="small"
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={() => {
                        setSelectedUserList(inviteUserList);
                        onClose();
                    }}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.add')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
