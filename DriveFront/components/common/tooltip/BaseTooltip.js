import { Tooltip } from '@mui/material';

export const BaseTooltip = ({ title, children, ...props }) => (
    <Tooltip title={title} disableInteractive {...props}>
        {children?.props?.disabled ? <span>{children}</span> : children}
    </Tooltip>
);
