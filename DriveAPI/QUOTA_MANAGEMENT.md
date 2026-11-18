# Managed single-account storage quota

`PATCH /api/public/v1/super-admin/tenants/{tenantSeq}` accepts the existing tenant update body (`tn`, `sd`, `bs`, `tq`, `ips`). `tq` is a positive byte count.

For **SYSTEM_MANAGED + SINGLE** tenants, the Super Admin changes the tenant limit and its sole protected account's allocation in one database transaction. The operation locks the tenant before the user. A decrease is permitted down to the account's current used bytes, including storage that Drive already counts toward usage. It never removes files. The tenant-admin user update endpoint remains forbidden for protected managed accounts.

A repeated request with the existing tenant limit also repairs an outdated account allocation. A missing account, multiple undeleted accounts, an unprotected account, insufficient physical capacity, or a limit below actual usage rejects the operation. A failure later in the tenant update rolls back both quota changes. Self-managed and MULTI tenants keep the existing allocation-sum lower bound.

`GET /api/public/v1/super-admin/tenants/quota-capacity?ts={tenantSeq}` retains `tenantAllocatedQuotaByte` and adds `tenantMinimumQuotaByte`. The new field is the input lower bound: at least one byte and, for managed SINGLE tenants, current used bytes; otherwise the sum of user allocations. The UI uses this lower bound. The server rechecks it under locks when saving.

Deploy Drive before a client that relies on this atomic synchronization. Developers must send one Super Admin tenant PATCH, including when the tenant limit is unchanged. Developers must not follow it with a tenant-admin user PATCH. No schema migration or tenant recreation is required for this quota change.

Tests: `ManagedSingleQuotaTest`, `ManagedSingleQuotaIntegrationTest`, existing tenant/quota/protected-account policy tests, and `DriveFront/scripts/test-tenant-quota.cjs`. Integration tests use a disposable PostgreSQL Testcontainer and verify committed limits, rollback after a later failure, rejection below usage, and concurrent usage changes.
