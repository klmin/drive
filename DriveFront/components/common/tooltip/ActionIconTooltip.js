// 액션 아이콘 메뉴 전용 툴팀 컴포넌트 (각 row 액션메뉴)
import { BaseTooltip } from './BaseTooltip';

export const ActionIconTooltip = ({ title, children }) => (
    <BaseTooltip title={title}>{children}</BaseTooltip>
);
