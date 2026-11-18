export const extractFilename = headers => {
    const contentDisposition = headers?.['content-disposition'] || '';

    const utf8FilenameMatch = contentDisposition.match(/filename\*\=UTF-8''(.+?)(?:;|$)/);
    if (utf8FilenameMatch) {
        return decodeURIComponent(utf8FilenameMatch[1]);
    }

    const fallbackMatch = contentDisposition.match(/filename="([^"]+)"/);
    if (fallbackMatch) {
        return fallbackMatch[1];
    }

    return 'downloaded_file';
};

// CSV 템플릿 파일 다운로드 시 사용
export const download = res => {
    const fileName = extractFilename(res.headers);

    const contentType = res.headers?.['content-type'] || 'application/octet-stream';

    const BOM = '\uFEFF';

    const blob = new Blob([BOM, res.data], { type: contentType });
    const url = window.URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
};

// 결과 CSV 파일 다운로드 시 사용
export const downloadCsvText = res => {
    const fileName = extractFilename(res.headers);

    // 서버가 내려준 CSV 텍스트 (문자열만 처리)
    const csvText = typeof res.data === 'string' ? res.data : String(res.data ?? '');

    // Excel 호환성을 위한 BOM
    const BOM = '\uFEFF';

    // CSV Blob 생성
    const blob = new Blob([BOM, csvText], { type: 'text/csv;charset=utf-8' });

    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
};
