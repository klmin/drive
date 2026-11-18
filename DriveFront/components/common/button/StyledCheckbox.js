import { Checkbox } from '@mui/material';
import { styled } from '@mui/material/styles';
import Image from 'next/image';
import palette from '@/theme/palette';

const CustomCheckbox = styled(Checkbox)(() => ({
    width: 24,
    height: 24,
    padding: 0,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: palette.btn_white_bg_hover,
    '&.Mui-checked': {
        color: palette.btn_white_bg_hover,
    },
    // 부분 선택(indeterminate) 상태의 색상
    '&.MuiCheckbox-indeterminate': {
        color: palette.btn_white_bg_hover,
    },
    '&:hover': {
        backgroundColor: palette.btn_white_bg_hover,
    },
    '&.Mui-focusVisible, &:focus': {
        outline: 'none',
        boxShadow: 'none',
    },
    '& .MuiSvgIcon-root': {
        display: 'none',
    },
    '&.Mui-checked .MuiSvgIcon-root': {
        display: 'none',
        color: palette.btn_white_bg_hover,
    },
}));

export default function StyledCheckbox({ checked, onChange, ...props }) {
    return (
        <CustomCheckbox
            {...props}
            checked={checked}
            onChange={onChange}
            icon={
                <span
                    style={{
                        width: 16,
                        height: 16,
                        border: '1px solid #D9D9D9',
                        borderRadius: 4,
                        display: 'inline-block',
                        boxSizing: 'border-box',
                    }}
                />
            }
            checkedIcon={
                <span
                    style={{
                        width: 24,
                        height: 24,
                        borderRadius: 4,
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <Image
                        src="/common/control-checkbox-medium-true.svg"
                        alt="checked"
                        width={24}
                        height={24}
                    />
                </span>
            }
        />
    );
}
