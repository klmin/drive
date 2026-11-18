#!/usr/bin/env python3
# Render only: does not contact Kubernetes or modify original SQL.
import argparse
import hashlib
import json
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--namespace', required=True)
args = parser.parse_args()
source = Path(__file__).resolve().parents[1] / 'thinkfree/TFD/template/database/postgres/init/initialize.sql'
raw = source.read_bytes()
name = 'drive-initialize-sql-' + hashlib.sha256(raw).hexdigest()[:16]
print(json.dumps({
 'apiVersion': 'v1', 'kind': 'ConfigMap',
 'metadata': {'name': name, 'namespace': args.namespace},
 'immutable': True, 'data': {'initialize.sql': raw.decode('utf-8')}
}, ensure_ascii=False))
