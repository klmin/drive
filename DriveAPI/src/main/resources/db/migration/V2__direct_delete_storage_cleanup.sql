-- Direct permanent deletion commits this cleanup work with metadata/quota changes.
-- Do not delete S3 objects before commit: a DB rollback cannot restore an S3 object.
-- No foreign key to resources/users: cleanup must survive removal of those records.
CREATE TABLE tb_direct_delete_cleanup (
 cleanup_seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 tenant_seq integer NOT NULL,
 object_key text NOT NULL,
 attempt_count integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,
 lease_until timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_direct_delete_cleanup_due ON tb_direct_delete_cleanup(next_attempt_at, lease_until);
COMMENT ON TABLE tb_direct_delete_cleanup IS 'Durable post-commit object deletion for direct permanent delete; retries do not retain a DB connection during storage I/O';
