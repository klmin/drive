export const ROUTES = {
    // User
    LOGIN: '/login',
    LOGIN_ERROR: '/login/error',
    LOGIN_API_KEY: '/login/api-key', // developers iframe 진입 — 부모 창이 보낸 API Key 로 진입
    HOME: '/home',
    PREVIEW: '/preview', // MCP 툴 webViewUrl 딥링크 — 레이아웃 없는 단독 미리보기 페이지 (PreviewDialog 재사용)
    DRIVE: '/drive',
    RECENT: '/recent',
    STARRED: '/starred',
    SHARED: '/shared',
    TRASH: '/trash',
    SETTING_GENERAL: '/setting?menu=general',
    SETTING_NOTIFICATIONS: '/setting?menu=notifications',

    // Auth flows
    OAUTH2_LOGIN: '/oauth2-login', // MCP OAuth 연동용 로그인 (백엔드 authorize가 미인증 시 이 라우트로 302)
    OAUTH2_CONSENT: '/oauth2-consent', // MCP OAuth 동의 화면 (백엔드 authorize가 동의 필요 시 이 라우트로 302)
    RESET_PASSWORD: '/reset-password',
    MFA: '/mfa',
    MFA_SETUP: '/mfa/setup',
    MFA_VERIFY: '/mfa/verify',
    SSO: '/sso',
    SSO_JWT: '/sso/jwt',
    LOGIN_SSO_SESSION_INVALID: '/login?error=SSO_SESSION_INVALID',

    // Share
    SHARE_LINK: '/s',
    SHARE_PUBLIC_PREVIEW: '/s/preview',
    SHARE_PUBLIC_FOLDER: '/s/folder',

    // Error
    ERROR_PAGE: '/error-page',
    ERROR_PAGE_ACCESS: '/error-page?reason=access',
    ERROR_PAGE_TENANT: '/error-page?reason=tenant',
    ERROR_PAGE_SHARE: '/error-page/share',

    // Admin
    ADMIN_BASE: '/admin',
    ADMIN_LOGIN: '/admin/login',
    ADMIN_LOGIN_API_KEY: '/admin/login/api-key', // developers iframe 관리자 진입 — 부모 창이 보낸 API Key 로 진입
    ADMIN_AUTH_ENTRY: '/admin/auth/entry',
    ADMIN_ACCOUNT: '/admin/account',
    ADMIN_ACCOUNTS_USERS: '/admin/accounts/users',
    ADMIN_ACCOUNTS_PENDING_DELETION: '/admin/accounts/pending-deletion',
    ADMIN_SETTINGS_SYSTEM_INFO: '/admin/settings/system-info',
    ADMIN_SETTINGS_LICENSE: '/admin/settings/license',

    // SuperAdmin
    SUPERADMIN_BASE: '/superadmin',
    SUPERADMIN_LOGIN: '/superadmin/login',
    SUPERADMIN_ACCOUNT: '/superadmin/account',
    SUPERADMIN_ACCOUNTS_TENANTS: '/superadmin/accounts/tenants',
    SUPERADMIN_SETTINGS_SYSTEM_INFO: '/superadmin/settings/system-info',
};

// Path segments used in AdminMenus / SuperAdminMenus renderSubItem
export const ADMIN_MENU_PATHS = {
    // accounts
    USERS: 'users',
    PENDING_DELETION: 'pending-deletion',
    // settings
    SYSTEM_INFO: 'system-info',
    SMTP: 'smtp',
    LICENSE: 'license',
    OFFICE: 'office',
    STORAGE: 'storage',
    INTELLECT: 'intellect',
    // security
    AUTHENTICATION: 'authentication',
    API_KEYS: 'api-keys',
    ACCESS_CONTROL: 'access-control',
    AUDIT_LOGS: 'audit-logs',
    // service
    BRANDING: 'branding',
    SHARE: 'share',
};

export const SUPERADMIN_MENU_PATHS = {
    // accounts
    TENANTS: 'tenants',
    API_KEYS: 'api-keys',
    // settings
    SYSTEM_INFO: 'system-info',
    // service
    SHARE: 'share',
    LOGIN_SETTINGS: 'login-settings',
};
