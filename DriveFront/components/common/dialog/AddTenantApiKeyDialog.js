import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Autocomplete,
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    IconButton,
    MenuItem,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import debounce from 'just-debounce-it';
import StyledButton from '@/components/common/button/StyledButton';
import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledSelect from '@/components/common/input/StyledSelect';
import StyledDatePicker from '@/components/common/input/StyledDatePicker';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { fetchAllSuperAdminTenants } from '@/utils/superAdminTenants';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import dayjs from 'dayjs';

/** 만료일 기본값 — 발급 시점 기준 90일 */
const DEFAULT_EXPIRE_DAYS = 90;

/** 발급 화면에서 고를 수 있는 scope 조합 — 백엔드 ApiKeyScope.ALLOWED_COMBINATIONS 와 같아야 한다 */
const SCOPE_OPTIONS = [
    { key: 'fullAccess', scopes: ['api:read', 'api:write', 'mcp:read', 'mcp:write'] },
    { key: 'readOnly', scopes: ['api:read', 'mcp:read'] },
];

/** 키 역할로 고를 수 있는 역할 — 대상 계정이 가진 것 중에서만(서버 검사) */
const KEY_ROLES = ['ADMIN', 'USER'];

const SEARCH_DEBOUNCE_MS = 300;
const MAX_USER_OPTIONS = 50;

const INITIAL_FORM = {
    tenantSeq: '',
    keyName: '',
    purpose: '',
    roleType: '',
    scopeKey: 'fullAccess',
    isProtected: false,
};

const textFieldSx = {
    width: '100%',
    '& .MuiInputBase-input': {
        color: palette.text_80,
    },
};

const formLabelSx = {
    ...typography.body5,
    color: palette.text_80,
    '& .MuiFormLabel-asterisk': {
        color: palette.status_error,
        fontWeight: typography.body4.fontWeight,
    },
};

/** StyledTextField 와 같은 테두리로 맞춘 검색 입력 */
const searchInputSx = {
    width: '100%',
    '& .MuiInputBase-root': {
        height: 40,
        backgroundColor: palette.btn_white_bg_normal,
        borderRadius: '4px',
        padding: '0 12px !important',
    },
    '& .MuiOutlinedInput-notchedOutline': {
        borderColor: palette.control_border_normal,
    },
    '& .MuiInputBase-input': {
        ...typography.body5,
        color: palette.text_80,
        padding: '0 !important',
    },
};

const userLabelOf = user => `${user.userName}(${user.userId})`;

function requireField(value, message) {
    if (!value.trim()) throw new Error(message);
}

/**
 * 슈퍼관리자 — 테넌트 계정 대상 API Key 발급(POST /super-admin/api-keys).
 * 테넌트 → 대상 계정 → 키 역할 순으로 고른다. 보호 키(pt)는 관리자·사용자 화면에서 만료일·활성화·비활성화·재발급을 할 수 없다.
 * 발급된 키 원문은 호출 측의 완료 다이얼로그(onCreated)가 보여준다.
 */
export default function AddTenantApiKeyDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { onCreated, reload, defaultTenantSeq } = props;

    const [formData, setFormData] = useState(INITIAL_FORM);
    const [expireDate, setExpireDate] = useState(null);
    const [tenants, setTenants] = useState([]);
    const [targetUser, setTargetUser] = useState(null);
    const [userKeyword, setUserKeyword] = useState('');
    const [userOptions, setUserOptions] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [errorMessageKeyName, setErrorMessageKeyName] = useState('');
    const [errorMessagePurpose, setErrorMessagePurpose] = useState('');

    // 늦게 도착한 이전 검색 응답이 최신 결과를 덮지 않도록 순번으로 거른다
    const requestSeqRef = useRef(0);

    useEffect(() => {
        if (!open) return;
        setFormData({ ...INITIAL_FORM, tenantSeq: defaultTenantSeq ?? '' });
        setExpireDate(dayjs().add(DEFAULT_EXPIRE_DAYS, 'day'));
        setTargetUser(null);
        setUserKeyword('');
        setUserOptions([]);
        setErrorMessageKeyName('');
        setErrorMessagePurpose('');
        fetchTenants();
    }, [open]);

    const fetchTenants = async () => {
        try {
            // 테넌트가 한 페이지(1,000개)를 넘어도 모두 고를 수 있도록 마지막 페이지까지 읽는다
            setTenants(await fetchAllSuperAdminTenants());
        } catch (err) {
            console.error('Tenant list fetch failed:', err);
        }
    };

    const fetchUsers = useCallback(async (tenantSeq, keyword) => {
        const seq = ++requestSeqRef.current;
        try {
            const res = await api.Get(`super-admin/tenants/${tenantSeq}/users/search`, {
                params: { sk: keyword },
            });
            if (seq !== requestSeqRef.current) return;
            setUserOptions((res?.data?.data ?? []).slice(0, MAX_USER_OPTIONS));
        } catch (err) {
            if (seq !== requestSeqRef.current) return;
            console.error(err);
            setUserOptions([]);
        }
    }, []);

    const debouncedFetchUsers = useMemo(
        () => debounce((tenantSeq, keyword) => fetchUsers(tenantSeq, keyword), SEARCH_DEBOUNCE_MS),
        [fetchUsers]
    );

    useEffect(() => () => debouncedFetchUsers.cancel?.(), [debouncedFetchUsers]);

    const resetUser = () => {
        debouncedFetchUsers.cancel?.();
        requestSeqRef.current += 1;
        setTargetUser(null);
        setUserKeyword('');
        setUserOptions([]);
        setFormData(prev => ({ ...prev, roleType: '' }));
    };

    /** 대상 계정이 가진 역할 중 키 역할로 쓸 수 있는 것 */
    const assignableRoles = useMemo(
        () => KEY_ROLES.filter(role => (targetUser?.roles ?? []).includes(role)),
        [targetUser]
    );

    /** 역할 표시 이름 — 목록 화면과 같은 문구(관리자·사용자) */
    const roleLabel = role => t(role === 'ADMIN' ? 'common.label.admin' : 'common.label.user');

    const selectedTenant = tenants.find(it => it.tenantSeq === formData.tenantSeq);
    const singleTenant =
        selectedTenant?.tenantType === 'SYSTEM_MANAGED' && selectedTenant?.userMode === 'SINGLE';

    const isDisabled =
        isSubmitting ||
        !formData.tenantSeq ||
        !targetUser ||
        !formData.roleType ||
        !!errorMessageKeyName ||
        !!errorMessagePurpose ||
        !formData.keyName.trim() ||
        !formData.purpose.trim() ||
        // 지난 날짜는 서버가 거부하므로 오늘 이후만 허용
        expireDate?.isValid?.() !== true ||
        expireDate.endOf('day').isBefore(dayjs());

    const handleChange = e => {
        const { name, value } = e.target;
        if (name === 'tenantSeq') {
            // 테넌트가 바뀌면 그 테넌트 계정을 다시 고른다
            resetUser();
        }
        setFormData(prev => ({ ...prev, [name]: value }));

        try {
            if (name === 'keyName') {
                requireField(value, t('adminApiKeyDialog.error.keyName'));
                setErrorMessageKeyName('');
            }
            if (name === 'purpose') {
                requireField(value, t('adminApiKeyDialog.error.purpose'));
                setErrorMessagePurpose('');
            }
        } catch (error) {
            if (name === 'keyName') setErrorMessageKeyName(error.message);
            if (name === 'purpose') setErrorMessagePurpose(error.message);
        }
    };

    const handleUserInputChange = (event, next, reason) => {
        if (reason !== 'input') return;
        setUserKeyword(next);
        if (!next.trim() || !formData.tenantSeq) {
            setUserOptions([]);
            return;
        }
        debouncedFetchUsers(formData.tenantSeq, next);
    };

    const handleUserChange = (event, selected) => {
        setTargetUser(selected);
        setUserKeyword(selected ? userLabelOf(selected) : '');
        const roles = KEY_ROLES.filter(role => (selected?.roles ?? []).includes(role));
        // 역할이 하나면 그 역할로 고정, 둘이면 고르게 둔다(기본 ADMIN)
        setFormData(prev => ({ ...prev, roleType: roles[0] ?? '' }));
    };

    const handleSave = async () => {
        if (isDisabled) return;
        setIsSubmitting(true);
        try {
            const scopes = SCOPE_OPTIONS.find(option => option.key === formData.scopeKey)?.scopes;
            const res = await api.Post('super-admin/api-keys', {
                tenantSeq: formData.tenantSeq,
                userSeq: targetUser.userSeq,
                roleType: formData.roleType,
                keyName: formData.keyName,
                keyPurpose: formData.purpose,
                scopes,
                // 날짜만 보내고 만료 시각(그 날 23:59:59)은 서버가 정한다
                expireDate: expireDate.format('YYYY-MM-DD'),
                pt: formData.isProtected,
            });

            if (res?.code === 201) {
                await reload?.();
                onClose?.();
                onCreated?.(res?.data?.apiKey ?? '');
            } else {
                showSnackbar(t('adminApiKeyDialog.addFail'), 'error');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            disableEnforceFocus
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    pt: '32px',
                    px: '32px',
                    pb: '24px',
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                    maxHeight: '90vh',
                },
            }}
        >
            {/* 제목 + 닫기 버튼 */}
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1" color="text_100">
                    {t('superAdmin.apiKey.createDialog.title')}
                </Typography>
                <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Stack sx={{ pt: '4px', gap: '16px' }}>
                    {/* 테넌트 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('superAdmin.apiKey.createDialog.tenant')}
                        </FormLabel>
                        <StyledSelect
                            select
                            name="tenantSeq"
                            value={formData.tenantSeq}
                            onChange={handleChange}
                            height={40}
                            variant="outlined"
                            sx={textFieldSx}
                            slotProps={{ select: { displayEmpty: true } }}
                        >
                            <MenuItem value="" disabled>
                                <Typography variant="body5" color="text_40">
                                    {t('superAdmin.apiKey.createDialog.tenantPlaceholder')}
                                </Typography>
                            </MenuItem>
                            {tenants.map(tenant => (
                                <MenuItem key={tenant.tenantSeq} value={tenant.tenantSeq}>
                                    <Typography variant="body5">
                                        {tenant.tenantName} ·{' '}
                                        {t(
                                            `superAdmin.tenantManagement.tenantType.${tenant.tenantType}`
                                        )}
                                        {tenant.tenantType === 'SYSTEM_MANAGED'
                                            ? ` · ${t(`superAdmin.tenantManagement.userMode.${tenant.userMode}`)}`
                                            : ''}
                                    </Typography>
                                </MenuItem>
                            ))}
                        </StyledSelect>
                    </FormControl>

                    {/* 대상 계정 — 그 테넌트에서 이름·이메일로 검색 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('superAdmin.apiKey.createDialog.targetUser')}
                        </FormLabel>
                        <Autocomplete
                            disabled={!formData.tenantSeq}
                            options={userOptions}
                            value={targetUser}
                            inputValue={userKeyword}
                            onInputChange={handleUserInputChange}
                            onChange={handleUserChange}
                            getOptionLabel={userLabelOf}
                            isOptionEqualToValue={(option, selected) =>
                                option.userSeq === selected?.userSeq
                            }
                            // 서버가 이미 걸러 준 결과다 — 화면에서 다시 거르면 이름·이메일 교차 검색이 깨진다
                            filterOptions={x => x}
                            noOptionsText={t('superAdmin.apiKey.createDialog.noUser')}
                            renderOption={(optionProps, option) => {
                                const { key, ...rest } = optionProps;
                                return (
                                    <Box component="li" key={option.userSeq} {...rest}>
                                        <Typography variant="body5" color="text_80" noWrap>
                                            {userLabelOf(option)} ·{' '}
                                            {KEY_ROLES.filter(role =>
                                                (option.roles ?? []).includes(role)
                                            )
                                                .map(roleLabel)
                                                .join(', ')}
                                        </Typography>
                                    </Box>
                                );
                            }}
                            renderInput={params => (
                                <TextField
                                    {...params}
                                    placeholder={t(
                                        'superAdmin.apiKey.createDialog.targetUserPlaceholder'
                                    )}
                                    sx={searchInputSx}
                                />
                            )}
                        />
                    </FormControl>

                    {/* 키 역할 — 대상 계정이 가진 역할 중에서 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.apiKey.table.role')}
                        </FormLabel>
                        <StyledSelect
                            select
                            name="roleType"
                            value={formData.roleType}
                            onChange={handleChange}
                            disabled={assignableRoles.length < 2}
                            height={40}
                            variant="outlined"
                            sx={textFieldSx}
                            slotProps={{ select: { displayEmpty: true } }}
                        >
                            <MenuItem value="" disabled>
                                <Typography variant="body5" color="text_40">
                                    {t('superAdmin.apiKey.createDialog.rolePlaceholder')}
                                </Typography>
                            </MenuItem>
                            {assignableRoles.map(role => (
                                <MenuItem key={role} value={role}>
                                    <Typography variant="body5">{roleLabel(role)}</Typography>
                                </MenuItem>
                            ))}
                        </StyledSelect>
                    </FormControl>

                    {/* Scope */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.apiKey.createDialog.scopeLabel')}
                        </FormLabel>
                        <StyledSelect
                            select
                            name="scopeKey"
                            value={formData.scopeKey}
                            onChange={handleChange}
                            height={40}
                            variant="outlined"
                            sx={textFieldSx}
                        >
                            {SCOPE_OPTIONS.map(option => (
                                <MenuItem key={option.key} value={option.key}>
                                    <Typography variant="body5">
                                        {t(`admin.apiKey.scope.${option.key}`)}
                                    </Typography>
                                </MenuItem>
                            ))}
                        </StyledSelect>
                    </FormControl>

                    {/* Key 이름 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('adminApiKeyDialog.keyName')}
                        </FormLabel>
                        <StyledTextField
                            name="keyName"
                            value={formData.keyName}
                            onChange={handleChange}
                            placeholder={t('adminApiKeyDialog.error.keyName')}
                            error={!!errorMessageKeyName}
                            errorMessage={errorMessageKeyName}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    {/* 사용 용도 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('adminApiKeyDialog.purpose')}
                        </FormLabel>
                        <StyledTextField
                            name="purpose"
                            value={formData.purpose}
                            onChange={handleChange}
                            placeholder={t('adminApiKeyDialog.error.purpose')}
                            error={!!errorMessagePurpose}
                            errorMessage={errorMessagePurpose}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    {/* 만료일 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.apiKey.createDialog.expiryLabel')}
                        </FormLabel>
                        <StyledDatePicker
                            value={expireDate}
                            setValue={setExpireDate}
                            minDate={dayjs()}
                            fullWidth
                            height={40}
                            borderRadius={4}
                            iconSize={20}
                        />
                    </FormControl>

                    {/* 보호 키 */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <StyledCheckbox
                                checked={formData.isProtected}
                                onChange={e =>
                                    setFormData(prev => ({
                                        ...prev,
                                        isProtected: e.target.checked,
                                    }))
                                }
                            />
                            <Typography variant="body5" color="text_80">
                                {t('superAdmin.apiKey.createDialog.protected')}
                            </Typography>
                        </Box>
                        <Typography variant="body7" color="text_60" sx={{ whiteSpace: 'pre-line' }}>
                            {t('superAdmin.apiKey.createDialog.protectedDesc')}
                        </Typography>
                        {singleTenant && (
                            <Typography
                                variant="body7"
                                color="text_60"
                                sx={{ whiteSpace: 'pre-line' }}
                            >
                                {t('superAdmin.apiKey.createDialog.singleTenantDesc')}
                            </Typography>
                        )}
                    </Box>
                </Stack>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 4 }}>
                <StyledButton
                    onClick={onClose}
                    color="btn_border"
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                >
                    <Typography variant="body5" color="text_100">
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color="btn_point_bg"
                    variant="contained"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleSave}
                    disabled={isDisabled}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.create')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}
