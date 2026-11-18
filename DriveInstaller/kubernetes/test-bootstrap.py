#!/usr/bin/env python3
"""Run ONLY disposable Docker fixtures, never the configured production DB."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid

root = Path(__file__).resolve().parent
original = root.parent / "thinkfree/TFD/template/database/postgres/init/initialize.sql"
before = hashlib.sha256(original.read_bytes()).hexdigest()
image = os.environ.get("DRIVE_INIT_TEST_IMAGE", "postgres:18.6-alpine")
server = "drive-bootstrap-test-" + uuid.uuid4().hex[:10]
results = []

def run(args, **kwargs):
    return subprocess.run(args, text=True, capture_output=True, **kwargs)

def sql(db, text):
    proc = run(["docker", "exec", "-i", server, "psql", "-X", "-qAt",
                "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", db], input=text)
    if proc.returncode:
        raise RuntimeError(proc.stderr)
    return proc.stdout.strip()

with tempfile.TemporaryDirectory(prefix="drive-bootstrap-test-") as tmp:
    tmp = Path(tmp)
    env = tmp / "db.env"
    password = secrets.token_urlsafe(32)
    env.write_text("POSTGRES_PASSWORD=" + password + chr(10))
    env.chmod(0o600)
    clientenv = tmp / "client.env"
    clientenv.write_text(chr(10).join([
        "PGHOST=127.0.0.1", "PGPORT=5432", "PGUSER=postgres",
        "PGPASSWORD=" + password, "PGSSLMODE=disable", "PGDATABASE=empty"
    ]) + chr(10))
    clientenv.chmod(0o600)
    test_sql = tmp / "sql"
    test_sql.mkdir()
    (test_sql / "initialize.sql").write_bytes(original.read_bytes())

    def boot(db, source=test_sql, name=None):
        args = ["docker", "run", "--rm", "--network", "container:" + server,
                "--env-file", str(clientenv), "-e", "PGDATABASE=" + db,
                "-v", str(root) + ":/bootstrap:ro",
                "-v", str(source) + ":/sql:ro"]
        if name:
            args += ["--name", name]
        return run(args + [image, "/bin/sh", "/bootstrap/bootstrap.sh"], timeout=180)

    def check(label, condition):
        if not condition:
            raise AssertionError(label)
        results.append({"case": label, "result": "PASS"})
        print("PASS", label, flush=True)

    try:
        proc = run(["docker", "run", "-d", "--name", server,
                    "--env-file", str(env), image])
        if proc.returncode:
            raise RuntimeError(proc.stderr)
        for _ in range(60):
            if run(["docker", "exec", server, "pg_isready", "-U", "postgres"]).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Temporary PostgreSQL did not become ready")
        for db in ["empty", "concurrent", "failure", "partial", "interrupted"]:
            sql("postgres", 'CREATE DATABASE "' + db + '";')

        p = boot("empty")
        if p.returncode:
            raise RuntimeError("Empty initialization: " + p.stderr)
        check("empty DB initializes", "Initialization check passed" in p.stdout)
        fingerprint = sql("empty", "SELECT md5(string_agg(row_to_json(u)::text, '' ORDER BY user_seq)) FROM tb_user u;")
        check("restart preserves users and password hashes",
              boot("empty").returncode == 0 and fingerprint == sql("empty", "SELECT md5(string_agg(row_to_json(u)::text, '' ORDER BY user_seq)) FROM tb_user u;"))

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            procs = list(pool.map(lambda _: boot("concurrent"), range(2)))
        check("simultaneous startup initializes exactly once",
              all(p.returncode == 0 for p in procs)
              and sum("Initializing empty database" in p.stdout for p in procs) == 1)

        failing = tmp / "failing"; failing.mkdir()
        failing.joinpath("initialize.sql").write_text(original.read_text().replace("COMMIT;", "SELECT 1 / 0;\nCOMMIT;".replace("\n", chr(10))))
        p = boot("failure", failing)
        check("SQL failure blocks startup", p.returncode != 0)
        check("SQL failure rolls back tables",
              sql("failure", "SELECT count(*) FROM pg_tables WHERE schemaname='public';") == "0")
        check("retry after SQL correction succeeds", boot("failure").returncode == 0)

        sql("partial", "CREATE TABLE tb_tenant (id integer);")
        p = boot("partial")
        check("partial schema rejected without deleting existing table",
              p.returncode != 0 and "DRIVE_DB_INIT_PARTIAL" in p.stderr
              and sql("partial", "SELECT count(*) FROM pg_tables WHERE schemaname='public';") == "1")

        sql("empty", "UPDATE tb_user SET status='DELETED' WHERE user_seq=1;")
        p = boot("empty")
        check("inactive Super Admin rejected and preserved",
              p.returncode != 0 and "ADMIN_INACTIVE" in p.stderr
              and sql("empty", "SELECT status FROM tb_user WHERE user_seq=1;") == "DELETED")
        sql("empty", "DELETE FROM tb_user_role WHERE role_type='SUPER_ADMIN';")
        p = boot("empty")
        check("missing Super Admin role is not recreated", p.returncode != 0 and "ADMIN_MISSING" in p.stderr)

        sleeping = tmp / "sleeping"; sleeping.mkdir()
        sleeping.joinpath("initialize.sql").write_text(original.read_text().replace("COMMIT;", "SELECT pg_sleep(60);\nCOMMIT;".replace("\n", chr(10))))
        name = server + "-interrupted"
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(boot, "interrupted", sleeping, name)
            for _ in range(60):
                active = sql("postgres", "SELECT count(*) FROM pg_stat_activity WHERE datname='interrupted' AND query LIKE 'SELECT pg_sleep%';")
                if active == "1":
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError("Interrupted fixture did not reach sleep")
            run(["docker", "kill", name])
            future.result()
        # PostgreSQL may not detect disconnect until its current query finishes.
        sql("postgres", "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='interrupted';")
        check("interruption rolls back and releases session lock", boot("interrupted").returncode == 0)
        check("original initialize.sql unchanged", before == hashlib.sha256(original.read_bytes()).hexdigest())
        report = {"image": image, "originalSqlSha256": before, "results": results}
        out = Path(os.environ.get("DRIVE_INIT_TEST_REPORT", "/tmp/drive-bootstrap-test-report.json"))
        out.write_text(json.dumps(report, indent=2))
        print("Report:", out)
    finally:
        run(["docker", "rm", "-f", server + "-interrupted"])
        run(["docker", "rm", "-f", server])
