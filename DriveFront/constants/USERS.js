const USERS = [
    {
        name: 'active',
        label: '활성화',
        src: '/admin/common/icon-user-ic-user-active-24.svg',
        type: ['ACTIVE'],
    },
    {
        name: 'inactive',
        label: '비활성화',
        src: '/admin/common/icon-user-ic-user-inactive-24.svg',
        type: ['INACTIVE'],
    },
    {
        name: 'pendingDelete',
        label: '비활성화',
        src: '/admin/common/icon-user-ic-user-pending-delete-24.svg',
        type: ['PENDING_DELETE'],
    },
];

export const getUserByUserType = status => {
    if (status === undefined || status === null) return;
    const user = USERS.find(f => Array.isArray(f.type) && f.type.some(t => t === status));
    return user || USERS[0];
};
