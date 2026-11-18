const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = {};
vm.runInNewContext(
    fs
        .readFileSync(require('node:path').join(__dirname, '../../utils/officeSettings.js'), 'utf8')
        .replace(/export /g, '') +
        ';globalThis.payload=officeSettingsPayload;globalThis.errors=officeSettingsErrors;',
    context
);
const payload = value => JSON.parse(JSON.stringify(context.payload(value)));
test('disabled mode sends no address or secrets', () =>
    assert.deepEqual(
        payload({
            officeConnectionType: 'none',
            officeDomain: 'old',
            officeTicketServiceToken: 'old',
        }),
        { officeConnectionType: 'none' }
    ));
test('legacy excludes ticket token and retains adapter name', () =>
    assert.deepEqual(
        payload({
            officeConnectionType: 'onPremise',
            officeDomain: ' https://office.test ',
            officeAuthMode: 'legacy',
            officeTicketServiceToken: 'ignored',
            officeHttpStorageRequestJwtSecret: 'key',
            officeHttpStorageAdapterName: 'override',
        }),
        {
            officeConnectionType: 'onPremise',
            officeDomain: 'https://office.test',
            officeAuthMode: 'legacy',
            officeHttpStorageRequestJwtSecret: 'key',
            officeHttpStorageAdapterName: 'override',
        }
    ));
const errors = value => JSON.parse(JSON.stringify(context.errors(value)));
const valid = {
    officeConnectionType: 'onPremise',
    officeDomain: 'https://office.test',
    officeAuthMode: 'ticket',
    officeTicketServiceToken: 'token',
    officeHttpStorageAdapterName: 'drive_1',
    officeHttpStorageRequestJwtSecret: 'k'.repeat(32),
};
test('disabled mode has no input errors', () =>
    assert.deepEqual(errors({ officeConnectionType: 'none' }), {}));
test('complete on-premise settings have no input errors', () =>
    assert.deepEqual(errors(valid), {}));
test('empty required fields are reported as required', () =>
    assert.deepEqual(
        errors({
            ...valid,
            officeDomain: ' ',
            officeTicketServiceToken: '',
            officeHttpStorageAdapterName: '',
            officeHttpStorageRequestJwtSecret: '',
        }),
        {
            officeDomain: 'required',
            officeTicketServiceToken: 'required',
            officeHttpStorageAdapterName: 'required',
            officeHttpStorageRequestJwtSecret: 'required',
        }
    ));
test('legacy mode does not require ticket token', () =>
    assert.deepEqual(
        errors({ ...valid, officeAuthMode: 'legacy', officeTicketServiceToken: '' }),
        {}
    ));
test('malformed address, adapter name and short signing key are invalid', () =>
    assert.deepEqual(
        errors({
            ...valid,
            officeDomain: 'office.test',
            officeHttpStorageAdapterName: '-bad',
            officeHttpStorageRequestJwtSecret: 'short',
        }),
        {
            officeDomain: 'invalid',
            officeHttpStorageAdapterName: 'invalid',
            officeHttpStorageRequestJwtSecret: 'invalid',
        }
    ));
