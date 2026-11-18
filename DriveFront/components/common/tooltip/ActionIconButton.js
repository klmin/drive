import { IconButton } from '@mui/material';
import Image from 'next/image';
import { ActionIconTooltip } from '@/components/common/tooltip/ActionIconTooltip';

export default function ActionIconButton({ title, icon, alt, onClick, disabled, sx }) {
    return (
        <ActionIconTooltip title={title}>
            <span>
                <IconButton sx={{ p: '6px', ...sx }} disabled={disabled} onClick={onClick}>
                    <Image src={icon} alt={alt || title} width={20} height={20} />
                </IconButton>
            </span>
        </ActionIconTooltip>
    );
}
