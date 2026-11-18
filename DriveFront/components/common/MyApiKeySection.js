/** 설정 화면 하단의 API Key 목록 — 사용자·관리자·슈퍼관리자 공용. */
import React, { useState } from 'react';
import { Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import ApiKeyCreateButton from '@/components/common/button/ApiKeyCreateButton';
import ApiKeys from '@/components/views/admin/security/api-keys/ApiKeys';
import {
    ADMIN_API_KEY_COLUMNS,
    MY_API_KEY_COLUMNS,
    SUPER_ADMIN_API_KEY_COLUMNS,
} from '@/components/views/admin/security/api-keys/ApiKeyTable';
import useClientConfig from '@/hooks/useClientConfig';

/** 목록에 한 번에 보이는 행 수 — 넘치면 목록 안에서 스크롤한다 */
const MAX_VISIBLE_ROWS = 5;

/**
 * 역할별 키 API·발급 다이얼로그·컬럼.
 * 관리자·슈퍼관리자는 각자의 API Key 관리 화면과 같은 목록(관리자는 테넌트 전체, 슈퍼관리자는 전체)을 보여준다.
 * 다른 계정 대상 키도 섞이므로 역할·사용자 계정 컬럼을 보여준다.
 */
const ROLE_CONFIG = {
    USER: {
        basePath: 'users/api-keys',
        listParams: {},
        addDialog: 'addUserApiKey',
        columns: MY_API_KEY_COLUMNS,
    },
    ADMIN: {
        basePath: 'admins/api-keys',
        listParams: {},
        addDialog: 'addAdminApiKey',
        columns: ADMIN_API_KEY_COLUMNS,
    },
    SUPER_ADMIN: {
        basePath: 'super-admin/api-keys',
        listParams: {},
        addDialog: 'addSuperAdminApiKey',
        columns: SUPER_ADMIN_API_KEY_COLUMNS,
    },
};

export default function MyApiKeySection({ roleType }) {
    const t = useTranslations();
    const { openDialog } = useDialog();
    // 발급 다이얼로그는 목록 상태를 모르므로, 발급이 끝나면 이 값만 올려 목록에 재조회를 알린다
    const [reloadToken, setReloadToken] = useState(0);

    const config = ROLE_CONFIG[roleType] ?? ROLE_CONFIG.USER;
    // SINGLE 은 사용자·관리자 경로의 API Key 발급이 막혀 있다(TENANT_011) — 슈퍼관리자는 테넌트 밖이라 해당 없음
    const { isSingleUser } = useClientConfig();
    const createDisabled = roleType !== 'SUPER_ADMIN' && isSingleUser;

    // 관리자 발급 다이얼로그는 완료 화면을 스스로 띄우고, 나머지는 원문을 완료 다이얼로그로 넘긴다
    const handleAddApiKey = () =>
        openDialog(config.addDialog, {
            reload: () => setReloadToken(prev => prev + 1),
            onCreated: apiKey => openDialog('reissueApiKeyComplete', { apiKey, mode: 'create' }),
        });

    return (
        // 위 프로필 영역과 구분되도록 부모 gap(16px)에 32px 더 띄운다
        <Stack gap={4} sx={{ mt: 8 }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between">
                <Typography variant="h4_h5" color="text_100">
                    {t('AdminSidebar.ApiKeys')}
                </Typography>
                <ApiKeyCreateButton onClick={handleAddApiKey} disabled={createDisabled} />
            </Stack>

            {/* 역할이 바뀌면 다른 목록이므로 새로 마운트해 다시 조회한다 */}
            <ApiKeys
                key={roleType}
                reloadToken={reloadToken}
                listUrl={config.basePath}
                listParams={config.listParams}
                basePath={config.basePath}
                columns={config.columns}
                syncWithQuery={false}
                maxVisibleRows={MAX_VISIBLE_ROWS}
            />
        </Stack>
    );
}
