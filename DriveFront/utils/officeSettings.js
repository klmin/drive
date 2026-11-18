export const OFFICE_SETTINGS_PATH = 'admins/system-config/office';

/** 서버 검사와 같은 기준 */
export const OFFICE_SIGNING_KEY_MIN_LENGTH = 32;
export const OFFICE_ADAPTER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const OFFICE_DOMAIN_PATTERN = /^https?:\/\/\S+$/i;

/**
 * 필드별 입력 오류. 'required' 는 비어 있음, 'invalid' 는 형식 오류. 빈 객체면 저장 가능.
 */
export const officeSettingsErrors = values => {
    if (values.officeConnectionType !== 'onPremise') return {};
    const errors = {};
    const domain = values.officeDomain?.trim() || '';
    if (!domain) errors.officeDomain = 'required';
    else if (!OFFICE_DOMAIN_PATTERN.test(domain)) errors.officeDomain = 'invalid';
    if (values.officeAuthMode === 'ticket' && !values.officeTicketServiceToken?.trim()) {
        errors.officeTicketServiceToken = 'required';
    }
    const adapter = values.officeHttpStorageAdapterName?.trim() || '';
    if (!adapter) errors.officeHttpStorageAdapterName = 'required';
    else if (!OFFICE_ADAPTER_PATTERN.test(adapter)) errors.officeHttpStorageAdapterName = 'invalid';
    const secret = values.officeHttpStorageRequestJwtSecret || '';
    if (!secret) errors.officeHttpStorageRequestJwtSecret = 'required';
    else if (secret.length < OFFICE_SIGNING_KEY_MIN_LENGTH) {
        errors.officeHttpStorageRequestJwtSecret = 'invalid';
    }
    return errors;
};

export const officeSettingsPayload = values =>
    values.officeConnectionType === 'none'
        ? { officeConnectionType: 'none' }
        : {
              officeConnectionType: 'onPremise',
              officeDomain: values.officeDomain.trim(),
              officeAuthMode: values.officeAuthMode,
              officeHttpStorageAdapterName:
                  values.officeHttpStorageAdapterName?.trim() || undefined,
              officeHttpStorageRequestJwtSecret: values.officeHttpStorageRequestJwtSecret,
              ...(values.officeAuthMode === 'ticket'
                  ? { officeTicketServiceToken: values.officeTicketServiceToken }
                  : {}),
          };
