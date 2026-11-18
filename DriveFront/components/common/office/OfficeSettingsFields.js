import React, { useState } from 'react';
import {
    Alert,
    Box,
    ButtonBase,
    Collapse,
    FormControlLabel,
    FormLabel,
    IconButton,
    InputAdornment,
    RadioGroup,
    Stack,
    Typography,
} from '@mui/material';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import { useTranslations } from 'next-intl';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import StyledBadge from '@/components/common/button/StyledBadge';
import StyledTextField from '@/components/common/input/StyledTextField';
import ArrowIcon from '@/components/common/icons/ArrowIcon';
import { OFFICE_SIGNING_KEY_MIN_LENGTH, officeSettingsErrors } from '@/utils/officeSettings';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import { isMaskedValue } from '@/utils/masking';

const BADGE = {
    recommended: {
        bg: palette.tone_blue_bg,
        borderColor: palette.tone_blue_border,
        color: palette.tone_blue_text,
    },
    deprecated: {
        bg: palette.tone_amber_bg,
        borderColor: palette.tone_amber_border,
        color: palette.tone_amber_text,
    },
    comingSoon: {
        bg: palette.badge_license_expired_bg,
        borderColor: palette.badge_license_expired_border,
        color: palette.badge_license_expired_text,
    },
};

const formLabelSx = {
    ...typography.body5,
    color: palette.text_80,
    display: 'block',
    mb: '10px',
    '&.Mui-focused': { color: palette.text_80 },
    '& .MuiFormLabel-asterisk': {
        color: palette.status_error,
        fontWeight: typography.body4.fontWeight,
    },
};

const fieldSx = {
    '& .MuiFormHelperText-root': {
        ...typography.body7,
        color: palette.text_60,
        mt: '7px',
        overflowWrap: 'anywhere',
        '&.Mui-error': { color: palette.status_error },
    },
};

/** 서버가 숨긴 값(__MASKED__) 자리에 블러로 깔아 두는 가짜 글자 */
const MASKED_DUMMY = 'xxxxxxxxxxxxxxxxxxxxxxxx';

/** 숨긴 값은 입력 글자만 블러 — 라벨·도움말은 그대로 */
const maskedFieldSx = {
    ...fieldSx,
    '& .MuiInputBase-input': { filter: 'blur(5px)', userSelect: 'none' },
};

/** 구역 구분선 */
const sectionSx = {
    borderTop: `1px solid ${palette.divider_8}`,
    pt: '20px',
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
};

const radioLabelSx = { m: 0, gap: '6px' };

/** 연결 방식 카드 — 카드 전체가 라벨이라 어디를 눌러도 선택 */
const choiceCardSx = (selected, disabled) => ({
    m: 0,
    p: '16px',
    gap: '8px',
    alignItems: 'flex-start',
    borderRadius: '6px',
    border: 1,
    borderColor: selected ? 'primary.main' : palette.control_border_normal,
    background: disabled ? palette.bg_gray : palette.btn_white_bg_normal,
    transition: 'border-color 150ms ease',
    ...(!disabled &&
        !selected && {
            '&:hover': { borderColor: palette.control_border_hover },
        }),
    '& .MuiFormControlLabel-label': { flex: 1, minWidth: 0, pt: '2px' },
    '& .MuiFormControlLabel-label.Mui-disabled': { opacity: 0.6 },
});

/** 회색 안내 박스 */
export function InfoNote({ children }) {
    return (
        <Box
            sx={{
                p: 3,
                borderRadius: '6px',
                bgcolor: palette.bg_gray,
                border: `1px solid ${palette.divider_8}`,
            }}
        >
            <Typography variant="body7" color="text_80" sx={{ whiteSpace: 'pre-line' }}>
                {children}
            </Typography>
        </Box>
    );
}

function OfficeBadge({ type, label }) {
    return <StyledBadge {...BADGE[type]}>{label}</StyledBadge>;
}

/** 라벨 + 공용 입력칸 — 서버가 숨긴 값이면 가짜 글자를 블러로 보여준다(형식 오류 표시 없음) */
export function LabeledField({ id, label, required, ...props }) {
    const masked = isMaskedValue(props.value);
    return (
        <Box>
            <FormLabel htmlFor={id} required={required} sx={formLabelSx}>
                {label}
            </FormLabel>
            <StyledTextField
                id={id}
                required={required}
                fullWidth
                variant="outlined"
                height={40}
                sx={masked ? maskedFieldSx : fieldSx}
                {...props}
                {...(masked && { value: MASKED_DUMMY, error: false })}
            />
        </Box>
    );
}

function SecretInput({ label, ...props }) {
    const t = useTranslations('officeIntegration');
    const [visible, setVisible] = useState(false);
    return (
        <LabeledField
            required
            label={label}
            type={visible ? 'text' : 'password'}
            autoComplete="new-password"
            InputProps={{
                endAdornment: (
                    <InputAdornment position="end">
                        <IconButton
                            type="button"
                            disabled={props.disabled}
                            aria-label={t(visible ? 'hideSecret' : 'showSecret', { field: label })}
                            aria-pressed={visible}
                            onClick={() => setVisible(v => !v)}
                            sx={{ p: 0 }}
                        >
                            {visible ? (
                                <VisibilityOutlinedIcon fontSize="small" style={{ opacity: 0.8 }} />
                            ) : (
                                <VisibilityOffOutlinedIcon
                                    fontSize="small"
                                    style={{ opacity: 0.8 }}
                                />
                            )}
                        </IconButton>
                    </InputAdornment>
                ),
            }}
            {...props}
            inputProps={{ maxLength: 4096, ...props.inputProps }}
        />
    );
}

/** 제목을 눌러 펼치는 안내 */
function Expandable({ id, title, children }) {
    const [open, setOpen] = useState(false);
    return (
        <Box>
            <ButtonBase
                type="button"
                aria-expanded={open}
                aria-controls={id}
                onClick={() => setOpen(v => !v)}
                sx={{ gap: '4px', borderRadius: '4px', textAlign: 'left', fontFamily: 'inherit' }}
            >
                <Typography variant="body4" color="text_80" component="span">
                    {title}
                </Typography>
                <ArrowIcon
                    direction={open ? 'up' : 'down'}
                    sx={{ fontSize: 16, color: palette.text_60 }}
                />
            </ButtonBase>
            <Collapse in={open} id={id}>
                <Box pt="10px">{children}</Box>
            </Collapse>
        </Box>
    );
}

/**
 * Office 연결 입력 영역. 관리자 설정 화면(variant="page")과 슈퍼관리자 테넌트 다이얼로그(variant="dialog")가 함께 쓴다.
 * page: 2칸 카드·파란 테두리 패널·안내 펼침, dialog: 세로 카드·테두리 없음·안내 접힘.
 */
export default function OfficeSettingsFields({
    variant = 'page',
    idPrefix = 'office',
    values,
    onChange,
    disabled,
    savedAdapter,
    revision = 0,
}) {
    const t = useTranslations('officeIntegration');
    const isPage = variant === 'page';
    const errors = officeSettingsErrors(values);
    const change = field => event => onChange(field, event.target.value);
    /** 입력한 값의 형식 오류만 빨갛게 — 빈 필수 칸은 저장 버튼 비활성으로만 */
    const invalid = field => errors[field] === 'invalid';
    const id = name => `${idPrefix}-${name}`;

    // TODO: Expose Cloud Office only after automatic connection and recovery are implemented.
    // See the deferred Cloud Office integration task in DriveFront/README.md.
    const choices = [
        { value: 'onPremise', label: t('onPremise'), description: t('onPremiseChoice') },
        { value: 'none', label: t('none'), description: t('noneChoice') },
    ];

    const endpointGuide = (
        <Typography variant="body7" color="text_60" sx={{ overflowWrap: 'anywhere' }}>
            {t('endpointHelp')}
        </Typography>
    );
    const applySteps = (
        <Box
            component="ol"
            sx={{
                pl: '20px',
                m: 0,
                ...typography.body7,
                color: palette.text_60,
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
            }}
        >
            <li>{t('applySave')}</li>
            <li>{t('applyTest')}</li>
            <li>{t('applyOpen')}</li>
        </Box>
    );

    const details = (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <Box>
                <FormLabel component="div" required sx={formLabelSx}>
                    {t('mode')}
                </FormLabel>
                <RadioGroup
                    aria-label={t('mode')}
                    name="officeAuthMode"
                    value={values.officeAuthMode}
                    onChange={change('officeAuthMode')}
                    row
                    sx={{ gap: '24px' }}
                >
                    {[
                        { value: 'ticket', label: 'Ticket', badge: 'recommended' },
                        { value: 'legacy', label: 'Legacy', badge: 'deprecated' },
                    ].map(mode => (
                        <FormControlLabel
                            key={mode.value}
                            value={mode.value}
                            disabled={disabled}
                            control={<StyledRadioButton />}
                            sx={radioLabelSx}
                            label={
                                <Stack direction="row" gap="6px" alignItems="center">
                                    <Typography variant="body5" color="text_80">
                                        {mode.label}
                                    </Typography>
                                    <OfficeBadge type={mode.badge} label={t(mode.badge)} />
                                </Stack>
                            }
                        />
                    ))}
                </RadioGroup>
                <Typography variant="body7" color="text_60" component="p" sx={{ mt: '7px' }}>
                    {t(values.officeAuthMode === 'ticket' ? 'ticketHelp' : 'legacyHelp')}
                </Typography>
            </Box>
            <Box component="section" aria-labelledby={id('server-title')} sx={sectionSx}>
                <Typography id={id('server-title')} variant="body4" color="text_80" component="h3">
                    {t('serverSection')}
                </Typography>
                <Stack gap="20px">
                    <LabeledField
                        id={id('domain')}
                        required
                        label={t('address')}
                        helperText={t('addressHelp')}
                        value={values.officeDomain}
                        onChange={change('officeDomain')}
                        disabled={disabled}
                        error={invalid('officeDomain')}
                        type="url"
                        placeholder="https://office.example.com"
                        inputProps={{ maxLength: 2048 }}
                    />
                    {values.officeAuthMode === 'ticket' && (
                        <SecretInput
                            key={'token-' + revision}
                            id={id('ticket-token')}
                            label={t('token')}
                            helperText={t('tokenHelp')}
                            value={values.officeTicketServiceToken}
                            onChange={change('officeTicketServiceToken')}
                            disabled={disabled}
                        />
                    )}
                </Stack>
            </Box>
            <Box component="section" aria-labelledby={id('storage-title')} sx={sectionSx}>
                <Stack gap="4px">
                    <Typography
                        id={id('storage-title')}
                        variant="body4"
                        color="text_80"
                        component="h3"
                    >
                        {t('storageSection')}
                    </Typography>
                    <Typography variant="body7" color="text_60">
                        {t('adapterInstructions')}
                    </Typography>
                </Stack>
                <Stack gap="20px">
                    <LabeledField
                        id={id('adapter')}
                        required
                        label={t('adapter')}
                        value={values.officeHttpStorageAdapterName}
                        helperText={t('adapterHelp')}
                        onChange={change('officeHttpStorageAdapterName')}
                        disabled={disabled}
                        error={invalid('officeHttpStorageAdapterName')}
                        inputProps={{ maxLength: 128 }}
                    />
                    {savedAdapter && savedAdapter !== values.officeHttpStorageAdapterName && (
                        <Alert severity="warning">{t('adapterChangeWarning')}</Alert>
                    )}
                    <SecretInput
                        key={'key-' + revision}
                        id={id('signing-key')}
                        label={t('signingKey')}
                        helperText={t('signingKeyHelp')}
                        value={values.officeHttpStorageRequestJwtSecret}
                        onChange={change('officeHttpStorageRequestJwtSecret')}
                        disabled={disabled}
                        error={invalid('officeHttpStorageRequestJwtSecret')}
                        inputProps={{ minLength: OFFICE_SIGNING_KEY_MIN_LENGTH }}
                    />
                    {isPage ? (
                        <Box
                            sx={{
                                p: '16px',
                                borderRadius: '6px',
                                outline: `1px ${palette.divider_8} solid`,
                                outlineOffset: '-1px',
                                background: palette.btn_white_bg_normal,
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '6px',
                            }}
                        >
                            <Typography variant="body4" color="text_80">
                                {t('endpointTitle')}
                            </Typography>
                            {endpointGuide}
                        </Box>
                    ) : (
                        <Expandable id={id('endpoint-guide')} title={t('endpointTitle')}>
                            {endpointGuide}
                        </Expandable>
                    )}
                </Stack>
            </Box>
            <Box component="section" aria-labelledby={id('steps-title')} sx={sectionSx}>
                {isPage ? (
                    <>
                        <Typography
                            id={id('steps-title')}
                            variant="body4"
                            color="text_80"
                            component="h3"
                        >
                            {t('applyTitle')}
                        </Typography>
                        {applySteps}
                    </>
                ) : (
                    <Box id={id('steps-title')}>
                        <Expandable id={id('steps-guide')} title={t('applyTitle')}>
                            {applySteps}
                        </Expandable>
                    </Box>
                )}
            </Box>
        </Box>
    );

    return (
        <Stack gap="20px">
            <RadioGroup
                aria-label={t('connectionType')}
                name="officeConnectionType"
                value={values.officeConnectionType}
                onChange={change('officeConnectionType')}
                sx={{
                    display: 'grid',
                    gridTemplateColumns: isPage ? { xs: '1fr', md: 'repeat(2, 1fr)' } : '1fr',
                    gap: isPage ? '12px' : '8px',
                }}
            >
                {choices.map(choice => (
                    <FormControlLabel
                        key={choice.value}
                        value={choice.value}
                        disabled={disabled || choice.disabled}
                        control={<StyledRadioButton inputProps={{ 'aria-label': choice.label }} />}
                        sx={choiceCardSx(
                            values.officeConnectionType === choice.value,
                            choice.disabled
                        )}
                        label={
                            <Stack gap="4px">
                                <Typography variant="body4" color="text_80">
                                    {choice.label}
                                </Typography>
                                <Typography variant="body7" color="text_60">
                                    {choice.description}
                                </Typography>
                                {choice.disabled && (
                                    <Box mt="4px">
                                        <OfficeBadge type="comingSoon" label={t('comingSoon')} />
                                    </Box>
                                )}
                            </Stack>
                        }
                    />
                ))}
            </RadioGroup>
            {values.officeConnectionType === 'none' && <InfoNote>{t('noneHelp')}</InfoNote>}
            {values.officeConnectionType === 'onPremise' &&
                (isPage ? (
                    <Box
                        component="section"
                        aria-labelledby={id('details-title')}
                        sx={{
                            width: '100%',
                            p: '22px',
                            borderRadius: '6px',
                            border: 1,
                            borderColor: 'primary.main',
                            background: palette.bg_gray,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '18px',
                        }}
                    >
                        <Stack gap="4px">
                            <Typography
                                id={id('details-title')}
                                sx={{ typography: 'subtitle1', color: palette.text_80 }}
                            >
                                {t('detailsTitle')}
                            </Typography>
                            <Typography variant="body7" color="text_60">
                                {t('detailsHelp')}
                            </Typography>
                        </Stack>
                        {details}
                    </Box>
                ) : (
                    <Box sx={sectionSx}>{details}</Box>
                ))}
        </Stack>
    );
}
