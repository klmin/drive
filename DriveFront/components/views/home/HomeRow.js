import useClientConfig from '@/hooks/useClientConfig';
import { getItemExt } from '@/constants/FILES';
import React from 'react';

/**
 * HomeTable 전용 Row
 */
export const HomeRow = React.memo(
    function HomeRow(props) {
        const {
            item,
            index,
            checked,

            // 표시 조건/레이아웃
            showLike,
            showShare,

            // utils/components
            palette,
            getFileByFileType,

            Image,
            Stack,
            TableRow,
            TableCell,
            Typography,
            IconButton,
            StyledCheckbox,
            BaseTooltip,
            ResourceActionButtons,

            disablePreview,
            disableDownload,
            disableShare,

            // handlers
            onRowMouseDown,
            onRowClickCombined,
            onPreview,
            onToggleCheck,
            onContextMenuOpen,
            onStar,
            onDownload,
            openDialog,
            getData,

            hasMultipleSelection,
            t,
        } = props;

        const { shareEnabled } = useClientConfig();

        return (
            <TableRow
                className={`home-row ${checked ? 'is-checked' : ''}`}
                key={item.resourceSeq ?? index}
                onMouseDown={onRowMouseDown}
                onClick={e => onRowClickCombined(e, item)}
                onDoubleClick={e => {
                    onRowClickCombined(e, item);
                    onPreview(item, { preferEdit: true });
                }}
                onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    onContextMenuOpen(e, item);
                }}
                sx={{
                    cursor: 'pointer',
                    borderBottom: `1px solid ${palette.table_divider_nomal}`,
                    background: checked ? palette.table_bg_selected : palette.white,
                }}
            >
                {/* 체크/아이콘 영역 + 리소스명 */}
                <TableCell
                    style={{ paddingRight: '2px' }}
                    sx={{
                        px: 2,
                        py: 2.5,
                        width: '100%',
                        minWidth: 0,
                        maxWidth: 50,
                    }}
                >
                    <Stack direction="row" alignItems="center" gap={3} sx={{ minWidth: 0 }}>
                        {/* md 이상: 체크박스 + 아이콘 겹쳐서 표시 (CSS hover/checked) */}
                        <Stack
                            direction="row"
                            justifyContent="center"
                            alignItems="center"
                            sx={{ display: { xs: 'none', md: 'flex' } }}
                            onClick={e => {
                                e.stopPropagation();
                                onToggleCheck(item);
                            }}
                        >
                            <div className="toggle-wrap">
                                <span className="toggle-layer toggle-checkbox">
                                    <StyledCheckbox checked={checked} />
                                </span>
                                <span className="toggle-layer toggle-icon">
                                    <Image
                                        src={
                                            getFileByFileType(getItemExt(item), item?.resourceType)
                                                ?.src
                                        }
                                        alt={
                                            getFileByFileType(getItemExt(item), item?.resourceType)
                                                ?.name
                                        }
                                        width={24}
                                        height={24}
                                    />
                                </span>
                            </div>
                        </Stack>

                        {/* md 미만: 체크박스 + 아이콘 표시 */}
                        <Stack
                            direction="row"
                            justifyContent="center"
                            alignItems="center"
                            sx={{ display: { xs: 'flex', md: 'none', gap: 2 } }}
                            onClick={e => {
                                e.stopPropagation();
                                onToggleCheck(item);
                            }}
                        >
                            <StyledCheckbox checked={checked} />
                            <Image
                                src={getFileByFileType(getItemExt(item), item?.resourceType)?.src}
                                alt={getFileByFileType(getItemExt(item), item?.resourceType)?.name}
                                width={24}
                                height={24}
                            />
                        </Stack>

                        {/* 리소스명 */}
                        <Stack
                            direction="row"
                            alignItems="center"
                            gap={1}
                            sx={{ width: '100%', minWidth: 0, overflow: 'hidden' }}
                        >
                            <BaseTooltip
                                title={item?.resourceName}
                                disableHoverListener={(item?.resourceName?.length ?? 0) <= 15}
                            >
                                <Typography
                                    variant="body5"
                                    color="text_80"
                                    noWrap
                                    onClick={e => {
                                        // 이름 클릭 = 미리보기 (기존 동작 유지)
                                        e.preventDefault();
                                        e.stopPropagation();
                                        onPreview(item, { preferEdit: true });
                                    }}
                                    sx={{
                                        minWidth: 0,
                                        '&:hover': { textDecoration: 'underline' },
                                    }}
                                >
                                    {item?.resourceName}
                                </Typography>
                            </BaseTooltip>

                            {shareEnabled && showShare && item?.isShared && (
                                <Image
                                    src="/menu/icon-ic-share-outline.svg"
                                    alt="share"
                                    width={16}
                                    height={16}
                                />
                            )}
                        </Stack>
                    </Stack>
                </TableCell>

                {/* 중요표시 */}
                <TableCell
                    style={{ paddingLeft: 0 }}
                    sx={{
                        pr: 2,
                        py: 2.5,
                        width: 36,
                        display: { xs: 'none', md: 'table-cell' },
                    }}
                >
                    <Stack direction="row" alignItems="center">
                        {(showLike || item?.isStarred) && (
                            <BaseTooltip title={t('actionIconTooltip.removeToStarred') ?? ''}>
                                <IconButton
                                    onClick={e => {
                                        e.stopPropagation();
                                        onStar(item);
                                    }}
                                >
                                    <Image
                                        src="/common/icon-ic-star-checked.svg"
                                        alt="star"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}
                    </Stack>
                </TableCell>

                {/** 액션 아이콘 메뉴 */}
                <TableCell
                    sx={{
                        width: { xs: 40, md: 200 },
                        minWidth: 200,
                    }}
                >
                    <Stack
                        direction="row"
                        spacing={1}
                        alignItems="center"
                        justifyContent="flex-end"
                    >
                        {/* md 이상: hover시에만 표시 */}
                        <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            sx={{ display: { xs: 'none', md: 'flex' } }}
                        >
                            <div className="row-actions">
                                <ResourceActionButtons
                                    item={item}
                                    disablePreview={disablePreview}
                                    disableDownload={disableDownload}
                                    disableShare={disableShare}
                                    onPreview={onPreview}
                                    onDownload={onDownload}
                                    onShare={it =>
                                        openDialog('fileShare', {
                                            item: it,
                                            reload: getData,
                                        })
                                    }
                                    onToggleStar={onStar}
                                    shareEnabled={shareEnabled}
                                />
                            </div>
                        </Stack>

                        {/* xs:  중요 표시 */}
                        {(showLike || item?.isStarred) && (
                            <BaseTooltip
                                title={props.t?.('actionIconTooltip.removeToStarred') ?? ''}
                            >
                                <IconButton
                                    sx={{ display: { xs: 'flex', md: 'none' } }}
                                    onClick={e => {
                                        e.stopPropagation();
                                        onStar(item);
                                    }}
                                >
                                    <Image
                                        src="/common/icon-ic-star-checked.svg"
                                        alt="star"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}

                        <span
                            style={{
                                cursor: hasMultipleSelection ? 'default' : 'pointer',
                                display: 'inline-flex',
                            }}
                            onClick={e => e.stopPropagation()}
                        >
                            <BaseTooltip title={props.t?.('tooltip.moreOptions') ?? ''}>
                                <IconButton
                                    onClick={e => {
                                        if (hasMultipleSelection) return;
                                        e.preventDefault();
                                        e.stopPropagation();
                                        onContextMenuOpen(e, item);
                                    }}
                                    id="dots-button"
                                    disabled={hasMultipleSelection}
                                >
                                    <Image
                                        src="/common/icon-ic-dots-vertical.svg"
                                        alt="dot"
                                        id="dots"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        </span>
                    </Stack>
                </TableCell>
            </TableRow>
        );
    },
    (prev, next) => {
        // 체크 변화가 있는 row만 리렌더 (전체 리렌더 x)
        return (
            prev.item?.resourceSeq === next.item?.resourceSeq &&
            prev.item?.resourceName === next.item?.resourceName &&
            prev.item?.isStarred === next.item?.isStarred &&
            prev.item?.isShared === next.item?.isShared &&
            prev.checked === next.checked &&
            prev.drawerOpen === next.drawerOpen &&
            prev.showLike === next.showLike &&
            prev.showShare === next.showShare &&
            prev.hasMultipleSelection === next.hasMultipleSelection
        );
    }
);
