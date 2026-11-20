'use client';

import AlertDialog from '@/components/common/dialog/AlertDialog';
import AddUserGroupDialog from '@/components/common/dialog/AddUserGroupDialog';
import FileCopyDialog from '@/components/common/dialog/FileCopyDialog';
import FileMoveDialog from '@/components/common/dialog/FileMoveDialog';
import FolderNameDialog from '@/components/common/dialog/FolderNameDialog';
import FindPassword from '@/components/common/dialog/FindPassword';
import FileNameDialog from '@/components/common/dialog/FileNameDialog';
import FolderFixDialog from '@/components/common/dialog/FolderFixDialog';
import FolderCreateDialog from '@/components/common/dialog/FolderCreateDialog';
import UploadDialog from '@/components/common/dialog/UploadDialog';
import PendingUploadsDialog from '@/components/common/dialog/PendingUploadsDialog';
import ChangePassword from '@/components/common/dialog/ChangePassword';
import InitialPasswordChangeDialog from '@/components/common/dialog/InitialPasswordChangeDialog';
import PreviewDialog from '@/components/common/dialog/PreviewDialog';
import VersionDialog from '@/components/common/dialog/Version';
import { useRouter } from '@/hooks/useRouter';
import UpdateUserDialog from '@/components/common/dialog/UpdateUserDialog';
import RegisterUserDialog from '@/components/common/dialog/RegisterUserDialog';
import BulkUserImportDialog from './BulkUserImportDialog';
import FileDuplicateDialog from './FileDuplicateDialog';
import AddAdminApiKeyDialog from './AddAdminApiKeyDialog';
import AddUserApiKeyDialog from './AddUserApiKeyDialog';
import AddSuperAdminApiKeyDialog from './AddSuperAdminApiKeyDialog';
import AddTenantApiKeyDialog from './AddTenantApiKeyDialog';
import TenantLicenseDialog from './TenantLicenseDialog';
import TenantOfficeDialog from './TenantOfficeDialog';
import ReissueApiKeyCompleteDialog from './ReissueApiKeyCompleteDialog';
import UpdateApiKeyExpireDateDialog from './UpdateApiKeyExpireDateDialog';
import FileShareDialog from './FileShareDialog';
import AddTenantDialog from './AddTenantDialog';
import UpdateTenantDialog from './UpdateTenantDialog';
import { ROUTES } from '@/constants/routes';

export default function GlobalDialog({ dialogs, onClose }) {
    const router = useRouter();

    if (!dialogs || dialogs.length === 0) {
        return null;
    }

    if (router.pathname === ROUTES.LOGIN) {
        dialogs = dialogs.filter(d => d.type !== 'uploadFiles');
    }

    const modalMap = {
        alert: AlertDialog,
        addUser: AddUserGroupDialog,
        fileName: FileNameDialog,
        fileCopy: FileCopyDialog,
        fileMove: FileMoveDialog,
        fileShare: FileShareDialog,
        folderFix: FolderFixDialog,
        folderCreate: FolderCreateDialog,
        folderName: FolderNameDialog,
        findPassword: FindPassword,
        changePassword: ChangePassword,
        initialPasswordChange: InitialPasswordChangeDialog,
        uploadFiles: UploadDialog,
        pendingUploads: PendingUploadsDialog,
        preview: PreviewDialog,
        version: VersionDialog,
        updateUser: UpdateUserDialog,
        registerUser: RegisterUserDialog,
        bulkUserImport: BulkUserImportDialog,
        fileDuplicate: FileDuplicateDialog,
        addAdminApiKey: AddAdminApiKeyDialog,
        addUserApiKey: AddUserApiKeyDialog,
        addSuperAdminApiKey: AddSuperAdminApiKeyDialog,
        addTenantApiKey: AddTenantApiKeyDialog,
        tenantLicense: TenantLicenseDialog,
        tenantOffice: TenantOfficeDialog,
        reissueApiKeyComplete: ReissueApiKeyCompleteDialog,
        updateApiKeyExpireDate: UpdateApiKeyExpireDateDialog,
        addTenant: AddTenantDialog,
        editTenant: UpdateTenantDialog,
    };

    return (
        <>
            {dialogs.map(dialog => {
                const { id, type, props } = dialog;

                const Component = modalMap[type];

                if (!Component) {
                    return null;
                }

                return <Component key={id} open={true} {...props} onClose={dialog.props.onClose} />;
            })}
        </>
    );
}
