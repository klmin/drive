import { SvgIcon } from '@mui/material';

export function ArrowFilledDown({
    width = 12,
    height = 12,
    opacity = '.8',
    fill = '#3A3D49',
    ...props
}) {
    return (
        <svg
            width={width}
            height={height}
            viewBox="0 0 12 12"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            {...props}
            style={{ cursor: 'pointer' }}
        >
            <path
                opacity={opacity}
                d="M2.854 4.854A.5.5 0 0 1 3.207 4h5.586a.5.5 0 0 1 .353.854L6.354 7.646a.5.5 0 0 1-.708 0L2.854 4.854z"
                fill={fill}
            />
        </svg>
    );
}

export function ArrowFilledUp({
    width = 12,
    height = 12,
    opacity = '.8',
    fill = '#3A3D49',
    ...props
}) {
    return (
        <svg
            width={width}
            height={height}
            viewBox="0 0 12 12"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            {...props}
            style={{ cursor: 'pointer' }}
        >
            <path
                opacity={opacity}
                transform="rotate(180 6 6)"
                d="M2.854 4.854A.5.5 0 0 1 3.207 4h5.586a.5.5 0 0 1 .353.854L6.354 7.646a.5.5 0 0 1-.708 0L2.854 4.854z"
                fill={fill}
            />
        </svg>
    );
}

//
// export function ArrowFilledDown() {
//     return (
//         <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
//             <path opacity=".8"
//                   d="M2.854 4.854A.5.5 0 0 1 3.207 4h5.586a.5.5 0 0 1 .353.854L6.354 7.646a.5.5 0 0 1-.708 0L2.854 4.854z"
//                   fill="#3A3D49"/>
//         </svg>
//
//     );
// }

export function ArrowFiledRight({ width = 12, height = 12, opacity = '.8', ...props }) {
    return (
        <svg
            width={width}
            height={height}
            viewBox="0 0 12 12"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            {...props}
        >
            <path
                opacity={opacity}
                d="M4.854 9.146A.5.5 0 0 1 4 8.793V3.207a.5.5 0 0 1 .854-.353l2.792 2.792a.5.5 0 0 1 0 .708L4.854 9.146z"
                fill="#3A3D49"
            />
        </svg>
    );
}
