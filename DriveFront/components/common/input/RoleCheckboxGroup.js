import React from 'react';
import { FormControlLabel, FormHelperText, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { FieldErrorMessage } from '@/components/common/input/StyledTextField';

// 계정에 부여할 수 있는 역할(서버 RoleType.isRegistrable 과 같은 범위) — 화면 표시 순서
export const ASSIGNABLE_ROLES = ['USER', 'ADMIN'];

/**
 * 등록·수정 다이얼로그의 역할 선택(USER·ADMIN 체크박스).
 * lockedRoles: 해제할 수 없는 역할 → 안내 문구 ({ USER: '...', ADMIN: '...' }) — 체크된 상태에서만 잠근다.
 * lockedMessage: 있으면 역할 전체를 잠그고(추가·해제 모두 불가) 이 안내를 보인다 — 보호 계정 등.
 */
export default function RoleCheckboxGroup({ roles, onChange, lockedRoles = {}, lockedMessage }) {
    const t = useTranslations();

    const toggle = role => {
        const next = roles.includes(role) ? roles.filter(r => r !== role) : [...roles, role];
        onChange(ASSIGNABLE_ROLES.filter(r => next.includes(r)));
    };

    const helperTexts = lockedMessage
        ? [lockedMessage]
        : ASSIGNABLE_ROLES.filter(role => roles.includes(role) && lockedRoles[role]).map(
              role => lockedRoles[role]
          );

    return (
        <>
            <Stack direction="row" spacing={3} sx={{ pt: 1, pl: 1 }}>
                {ASSIGNABLE_ROLES.map(role => (
                    <FormControlLabel
                        key={role}
                        control={
                            <StyledCheckbox
                                name={`role-${role}`}
                                checked={roles.includes(role)}
                                disabled={
                                    !!lockedMessage || (roles.includes(role) && !!lockedRoles[role])
                                }
                                onChange={() => toggle(role)}
                            />
                        }
                        label={
                            <Typography variant={'body5'} color={'text_80'}>
                                {role === 'USER' ? t('common.label.user') : t('common.label.admin')}
                            </Typography>
                        }
                        sx={{ gap: 1, m: 0 }}
                    />
                ))}
            </Stack>
            {roles.length === 0 && <FieldErrorMessage message={t('AddUserDialog.roleRequired')} />}
            {helperTexts.map(text => (
                <FormHelperText key={text}>{text}</FormHelperText>
            ))}
        </>
    );
}
