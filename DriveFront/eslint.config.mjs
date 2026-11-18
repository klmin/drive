import { dirname } from 'path';
import { fileURLToPath } from 'url';
import { FlatCompat } from '@eslint/eslintrc';
import prettierRecommended from 'eslint-plugin-prettier/recommended';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
    baseDirectory: __dirname,
});

export default [
    ...compat.extends('next/core-web-vitals'),
    {
        ignores: [
            '.next/**',
            'out/**',
            'build/**',
            'node/**',
            'node_modules/**',
            '.claude/**',
            'public/mockServiceWorker.js',
        ],
    },
    {
        files: ['**/*.{js,ts,jsx,tsx}'],
        languageOptions: {
            ecmaVersion: 'latest',
            sourceType: 'module',
        },
        rules: {
            quotes: ['error', 'single', { avoidEscape: true }],
            semi: ['error', 'always'],
        },
    },
    prettierRecommended,
    {
        // Windows(core.autocrlf) 체크아웃의 CRLF 는 위반으로 보지 않음
        rules: { 'prettier/prettier': ['error', { endOfLine: 'auto' }] },
    },
    {
        // 테넌트 경로(/t/{slug}) 누락 방지 — 라우터는 hooks/useRouter.js 래퍼만 사용
        files: ['**/*.{js,jsx}'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        {
                            name: 'next/router',
                            message: "테넌트 접두를 붙이는 '@/hooks/useRouter' 를 사용하세요.",
                        },
                        {
                            name: 'next/link',
                            message:
                                '테넌트 접두가 붙지 않습니다. useRouter 래퍼나 tenantHref 를 사용하세요.',
                        },
                    ],
                },
            ],
            'no-restricted-syntax': [
                'warn',
                {
                    selector:
                        "AssignmentExpression[left.object.object.name='window'][left.object.property.name='location'][left.property.name='href'][right.callee.name!='tenantHref']",
                    message: 'window.location 직접 이동 — 테넌트 경로라면 tenantHref 로 감싸세요.',
                },
                {
                    selector:
                        "CallExpression[callee.object.object.name='window'][callee.object.property.name='location'][callee.property.name=/^(assign|replace)$/][arguments.0.callee.name!='tenantHref']",
                    message: 'window.location 직접 이동 — 테넌트 경로라면 tenantHref 로 감싸세요.',
                },
            ],
        },
    },
    {
        files: ['hooks/useRouter.js'],
        rules: { 'no-restricted-imports': 'off' },
    },
    {
        // 슈퍼관리자는 테넌트 경로 대상 아님
        files: [
            'pages/superadmin/**',
            'components/views/super-admin/**',
            'components/common/layout/SuperAdminAuthLayout.js',
        ],
        rules: { 'no-restricted-syntax': 'off' },
    },
];
