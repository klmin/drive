import React, { useState, useContext, createContext } from 'react';
import GlobalDialog from '@/components/common/dialog/GlobalDialog';

const DialogContext = createContext();

export const useDialog = () => useContext(DialogContext);

export const DialogProvider = ({ children }) => {
    const [dialogs, setDialogs] = useState([]);

    const openDialog = (type, props = {}) => {
        // 👇 함수 전체를 Promise로 감싸서 await가 가능하도록 합니다.
        return new Promise(resolve => {
            setDialogs(prevDialogs => {
                // --- 1. 'uploadFiles' 타입 특별 처리 (기존 로직 유지) ---
                if (type === 'uploadFiles') {
                    const existingDialogIndex = prevDialogs.findIndex(
                        d => d.type === 'uploadFiles'
                    );

                    if (existingDialogIndex !== -1) {
                        const updatedDialogs = [...prevDialogs];
                        const existingDialog = updatedDialogs[existingDialogIndex];

                        // 기존 props와 새 props를 병합
                        const newProps = {
                            ...existingDialog.props,
                            ...props,
                            files: [...(existingDialog.props.files || []), ...(props.files || [])],
                            folders: [
                                ...(existingDialog.props.folders || []),
                                ...(props.folders || []),
                            ],
                        };
                        updatedDialogs[existingDialogIndex] = {
                            ...existingDialog,
                            props: newProps,
                        };

                        // uploadFiles는 기다릴 필요가 없으므로 즉시 resolve하고, 수정된 배열을 반환
                        resolve(true);
                        return updatedDialogs;
                    }
                }

                const id = Date.now();
                const dialogProps = {
                    ...props,
                    onClose: () => {
                        closeDialog(id);
                        resolve(false);
                    },
                    onConfirm: async () => {
                        try {
                            if (props.onClick) {
                                await props.onClick();
                            }
                            resolve(true);
                        } catch (err) {
                            console.error('Dialog confirm error:', err);
                            resolve(false);
                            throw err;
                        } finally {
                            closeDialog(id);
                        }
                    },
                    onSelect: choice => {
                        resolve(choice);
                        closeDialog(id);
                    },
                };

                // 새로 생성된 다이얼로그를 배열에 추가
                return [...prevDialogs, { id, type, props: dialogProps }];
            });
        });
    };

    const closeDialog = id => {
        setDialogs(prevDialogs => prevDialogs.filter(dialog => dialog.id !== id));
    };

    const value = { openDialog, closeDialog };

    return (
        <DialogContext.Provider value={value}>
            {children}
            <GlobalDialog dialogs={dialogs} onClose={closeDialog} />
        </DialogContext.Provider>
    );
};
