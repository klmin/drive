// FileDropZone.jsx

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Box } from '@mui/material';

const traverseFileTree = (entry, path, files, folders) => {
    return new Promise(resolve => {
        path = path || '';
        if (entry.isFile) {
            entry.file(file => {
                const newFile = new File([file], file.name, {
                    type: file.type,
                    lastModified: file.lastModified,
                });

                Object.defineProperty(newFile, 'webkitRelativePath', {
                    value: path + file.name,
                    writable: true,
                    configurable: true,
                });

                files.push(newFile);
                resolve();
            });
        } else if (entry.isDirectory) {
            const dirReader = entry.createReader();
            const fullPath = path + entry.name;
            folders.push({ name: entry.name, path: fullPath, type: 'folder' });

            const readAllEntries = () => {
                dirReader.readEntries(entries => {
                    if (entries.length === 0) {
                        resolve();
                    } else {
                        Promise.all(
                            entries.map(childEntry =>
                                traverseFileTree(childEntry, fullPath + '/', files, folders)
                            )
                        ).then(readAllEntries);
                    }
                });
            };
            readAllEntries();
        }
    });
};

export default function FileDropZone({ onFilesDropped, children, isDragActive, setIsDragActive }) {
    const dragCounter = useRef(0);

    const handleDragEnter = e => {
        e.preventDefault();
        e.stopPropagation();
        if (!e.dataTransfer.types.includes('Files')) {
            return;
        }

        dragCounter.current++;
        if (dragCounter.current === 1) {
            setIsDragActive(true);
        }
    };

    const handleDragLeave = e => {
        e.preventDefault();
        e.stopPropagation();
        if (!e.dataTransfer.types.includes('Files')) {
            return;
        }
        dragCounter.current--;
        if (dragCounter.current === 0) {
            setIsDragActive(false);
        }
    };

    const handleDragOver = e => {
        e.preventDefault();
        e.stopPropagation();
        if (!e.dataTransfer.types.includes('Files')) {
            return;
        }
    };

    const handleDrop = useCallback(
        async e => {
            e.preventDefault();
            e.stopPropagation();
            if (!e.dataTransfer.types.includes('Files')) return;

            setIsDragActive(false);
            dragCounter.current = 0;

            const items = e.dataTransfer.items;
            const files = [];
            const folders = [];
            const promises = [];

            console.log('items');
            console.log(items);

            if (items && items.length) {
                console.log(items.length);
                for (let i = 0; i < items.length; i++) {
                    const entry = items[i].webkitGetAsEntry();
                    if (entry) {
                        promises.push(traverseFileTree(entry, '', files, folders));
                    }
                }
                await Promise.all(promises);

                onFilesDropped({ files, folders });
            }
        },
        [onFilesDropped]
    );

    useEffect(() => {
        const handleDragEnd = () => {
            dragCounter.current = 0;
            setIsDragActive(false);
        };

        window.addEventListener('drop', handleDragEnd);
        window.addEventListener('dragend', handleDragEnd);

        return () => {
            window.removeEventListener('drop', handleDragEnd);
            window.removeEventListener('dragend', handleDragEnd);
        };
    }, []);

    return (
        <Box
            sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                width: '100%',
            }}
            // style={dropzoneStyle}
            onDragEnter={handleDragEnter}
            onDragLeave={handleDragLeave}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
        >
            {children}
        </Box>
    );
}
