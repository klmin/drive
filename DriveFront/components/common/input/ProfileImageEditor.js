import { Box, IconButton, Menu, MenuItem } from '@mui/material';
import Image from 'next/image';
import { useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import ProfileImage from '@/components/common/input/ProfileImage';
import { PROFILE_IMAGE_ACCEPT, validateProfileImage } from '@/utils/userUtils';

/** 드롭다운 행 — 시안 규격 160×36 / 상하 패딩 6 (기본 menuItem.js는 md 미만 40이라 눌러준다) */
const menuItemSx = {
    height: 36,
    minHeight: { xs: 36, md: 36 },
    padding: '6px 0',
    justifyContent: 'space-between',
};

const menuItemLabelSx = {
    display: 'flex',
    alignItems: 'center',
    height: 24,
    pl: '16px',
    pr: '16px',
    ...typography.body5,
    color: palette.text_80,
};

/** 프로필 이미지와 수정 드롭다운(이미지 업로드 / 기본 이미지로 변경) — 서버 기준에 안 맞는 파일은 onInvalidImage(문구)로만 알린다 */
export default function ProfileImageEditor({ src, onSelectImage, onResetImage, onInvalidImage }) {
    const t = useTranslations();

    const fileInputRef = useRef(null);
    const editTriggerRef = useRef(null);
    const [anchorEl, setAnchorEl] = useState(null);

    const closeMenu = () => {
        setAnchorEl(null);
        editTriggerRef.current?.focus();
    };

    return (
        <Box sx={{ position: 'relative' }}>
            <Box
                sx={{
                    position: 'relative',
                    width: {
                        md: 72,
                        xs: 48,
                    },
                    height: {
                        md: 72,
                        xs: 48,
                    },
                }}
            >
                <ProfileImage src={src} alt="user" fill />
            </Box>
            <IconButton
                ref={editTriggerRef}
                onClick={e => setAnchorEl(e.currentTarget)}
                sx={{
                    padding: '6px',
                    position: 'absolute',
                    bottom: {
                        md: -10,
                        xs: -20,
                    },
                    right: {
                        md: -10,
                        xs: -20,
                    },
                }}
            >
                <Image
                    src={'/setting/icon-ic-edit_black.svg'}
                    alt={'edit'}
                    width={20}
                    height={20}
                    style={{ opacity: 0.8 }}
                />
            </IconButton>
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={closeMenu}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                PaperProps={{
                    sx: {
                        mt: 1,
                        minWidth: 160,
                        borderRadius: '6px',
                        backgroundColor: palette.white,
                        outline: `1px solid ${palette.divider_12}`,
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                    },
                }}
            >
                <MenuItem
                    sx={menuItemSx}
                    onClick={() => {
                        closeMenu();
                        fileInputRef.current?.click();
                    }}
                >
                    <Box sx={menuItemLabelSx}>{t('common.accountSetting.profileImage.upload')}</Box>
                </MenuItem>
                <MenuItem
                    sx={menuItemSx}
                    onClick={() => {
                        closeMenu();
                        onResetImage?.();
                    }}
                >
                    <Box sx={menuItemLabelSx}>
                        {t('common.accountSetting.profileImage.resetToDefault')}
                    </Box>
                </MenuItem>
            </Menu>
            <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                accept={PROFILE_IMAGE_ACCEPT}
                onChange={e => {
                    const file = e.target.files[0];
                    if (file) {
                        try {
                            validateProfileImage(file, t);
                            onSelectImage?.(file);
                        } catch (error) {
                            onInvalidImage?.(error.message);
                        }
                    }
                    e.target.value = ''; // 같은 파일 재선택도 변경으로 잡기 위해 초기화
                }}
            />
        </Box>
    );
}
