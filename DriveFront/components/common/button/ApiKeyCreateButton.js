/** API Key 생성 버튼 — 관리자 API Key 관리 화면과 설정 화면의 본인 키 목록이 같이 쓴다. */
import React from 'react';
import Image from 'next/image';
import { Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';

// 시안: 높이 28 / 좌 6·우 8 패딩. 테두리를 안쪽에 둬야(outline) 높이·폭에 1px씩 더해지지 않는다.
const createButtonSx = {
    height: 28,
    py: 0,
    pl: '6px',
    pr: 2,
    gap: 1,
    borderRadius: '4px',
    outline: `1px solid ${palette.control_border_normal}`,
    outlineOffset: '-1px',
    // 흰 아이콘이 비활성 배경에 묻혀서 텍스트와 같은 회색(action.disabled)으로 맞춘다
    '&.Mui-disabled img': {
        filter: 'brightness(0)',
        opacity: 0.26,
    },
};

export default function ApiKeyCreateButton({ onClick, disabled = false }) {
    const t = useTranslations();

    return (
        <StyledButton
            color="btn_point_bg"
            variant="contained"
            onClick={onClick}
            disabled={disabled}
            sx={createButtonSx}
        >
            <Image src="/common/icon-ic-plus-white.svg" alt="plus" width={20} height={20} />
            <Typography variant="body9" color="white">
                {t('adminApiKeys.addApiKey')}
            </Typography>
        </StyledButton>
    );
}
