// Run: node --experimental-vm-modules scripts/test-tenant-quota.cjs
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
(async () => {
    const source = new vm.SourceTextModule(
        fs.readFileSync(require('node:path').join(__dirname, '../utils/quota.js'), 'utf8')
    );
    await source.link(
        () =>
            new vm.SyntheticModule(['convertSizeToBytes'], function () {
                this.setExport('convertSizeToBytes', () => {
                    throw new Error('Unused in capacity validation');
                });
            })
    );
    await source.evaluate();
    const validate = source.namespace.validateTenantQuotaCapacity;
    const managed = {
        tenantTotalQuotaByte: 100,
        tenantAllocatedQuotaByte: 100,
        tenantMinimumQuotaByte: 30,
        storageAvailByte: null,
    };
    validate(30, managed, key => key);
    assert.throws(() => validate(29, managed, key => key), /validateBelowAllocated/);
    assert.throws(
        () => validate(50, { ...managed, tenantMinimumQuotaByte: 100 }, key => key),
        /validateBelowAllocated/
    );
    assert.throws(
        () => validate(201, { ...managed, storageAvailByte: 100 }, key => key),
        /validateInsufficient/
    );
    validate(200, { ...managed, storageAvailByte: 100 }, key => key);
    console.log('Tenant quota capacity UI: 5 checks passed');
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
