const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(enabled = true) {
    const calls = [];
    const source =
        fs
            .readFileSync(path.join(__dirname, '../../apis/webOfficeApi.js'), 'utf8')
            .replace(/^import .*;$/gm, '')
            .replace(/export /g, '') +
        '\n globalThis.apiUnderTest = {getOfficeViewUrl,getOfficeEditUrl};';
    const context = {
        console,
        getOfficeFormat: n =>
            ({ docx: 'word', xlsx: 'sheet', pptx: 'slide', pdf: 'pdf' })[n.split('.').pop()],
        resolveLocale: () => 'ko-KR',
        api: {
            Get: async () => ({
                status: 200,
                data: { data: { officeConnectionType: enabled ? 'onPremise' : 'none' } },
            }),
            Post: async (...args) => {
                calls.push(args);
                return {
                    code: 200,
                    data: {
                        url: 'https://office.test/cloud-office/api/open-document?ticket=issued',
                    },
                };
            },
        },
    };
    vm.runInNewContext(source, context);
    return { ...context.apiUnderTest, calls };
}
test('uses the saved Office mode by omitting an explicit authMode', async () => {
    const api = setup();
    const url = await api.getOfficeEditUrl(
        { resourceSeq: 12, sharedByUserSeq: 4, resourceName: 'a.docx', canDownload: true },
        { userSeq: 9, userName: 'untrusted name', userLanguage: 'ko' }
    );
    assert.equal(url, 'https://office.test/cloud-office/api/open-document?ticket=issued');
    assert.equal(api.calls[0][0], 'office/client-sessions');
    assert.deepEqual(JSON.parse(JSON.stringify(api.calls[0][1])), {
        ownerUserSeq: 4,
        resourceSeq: 12,
        app: 'WRITE_EDITOR',
        lang: 'ko_KR',
    });
});
test('read-only URLs use Viewer for every supported format', async () => {
    for (const [ext, app] of [
        ['docx', 'WRITE_VIEWER'],
        ['xlsx', 'CALC_VIEWER'],
        ['pptx', 'SHOW_VIEWER'],
        ['pdf', 'PDF_VIEWER'],
    ]) {
        const api = setup();
        await api.getOfficeViewUrl(
            { resourceSeq: 12, userSeq: 4, resourceName: 'a.' + ext },
            { userSeq: 4 }
        );
        assert.equal(api.calls[0][1].app, app);
    }
});
test('PDF edit entry remains read-only and never requests Watcher', async () => {
    const api = setup();
    await api.getOfficeEditUrl(
        { resourceSeq: 12, userSeq: 4, resourceName: 'a.pdf' },
        { userSeq: 4 }
    );
    assert.equal(api.calls[0][1].app, 'PDF_VIEWER');
});
test('missing document and unsupported files do not issue tickets', async () => {
    const api = setup();
    await api.getOfficeEditUrl(null, {});
    await api.getOfficeEditUrl({ resourceName: 'a.xyz' }, {});
    assert.equal(api.calls.length, 0);
});

test('explicit legacy selection is sent to BE', async () => {
    const api = setup();
    await api.getOfficeEditUrl(
        { resourceSeq: 12, userSeq: 4, resourceName: 'a.docx' },
        { userSeq: 4 },
        false,
        'legacy'
    );
    assert.equal(api.calls[0][0], 'office/client-sessions');
    assert.equal(api.calls[0][1].authMode, 'legacy');
});

test('disabled Office does not request a document URL', async () => {
    const api = setup(false);
    assert.equal(
        await api.getOfficeEditUrl({ resourceSeq: 12, resourceName: 'a.docx' }, { userSeq: 4 }),
        undefined
    );
    assert.equal(api.calls.length, 0);
});
