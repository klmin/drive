import { useState, useEffect, useCallback } from 'react';

export default function useDragDrop({ onDrop }) {
    const [isDraggingOver, setIsDraggingOver] = useState(false);

    const handleDragEnter = useCallback(e => {
        e.preventDefault();
        e.stopPropagation();
        onDrop();
    }, []);

    useEffect(() => {
        const dropZone = document.body;
        if (!dropZone) return;

        dropZone.addEventListener('dragenter', handleDragEnter);
        // dropZone.addEventListener('dragleave', handleDragLeave);
        // dropZone.addEventListener('dragover', handleDragOver);
        // dropZone.addEventListener('drop', handleDrop);

        return () => {
            dropZone.removeEventListener('dragenter', handleDragEnter);
            // dropZone.removeEventListener('dragleave', handleDragLeave);
            // dropZone.removeEventListener('dragover', handleDragOver);
            // dropZone.removeEventListener('drop', handleDrop);
        };
    }, [handleDragEnter]);

    return { isDraggingOver };
}
