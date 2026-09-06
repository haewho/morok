#!/usr/bin/env python3
"""Interoperability test using temporary test-only RSA keys; never contacts a server."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('sign_pool', root / 'infra/sign-pool.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
now = int(time.time())
config = {'version': 1, 'issued': now - 60, 'expires': now + 3600, 'nodes': [
    {'id': 'primary', 'link': 'tg://socks?server=first.example&port=1080&user=test&pass=p%2Bass'},
    {'id': 'backup', 'link': 'tg://proxy?server=second.example&port=443&secret=0123456789abcdef0123456789abcdef'}]}
module.build_payload(config)
bad = dict(config, expires=now - 1)
try:
    module.build_payload(bad)
    raise AssertionError('Expired data accepted')
except ValueError:
    pass
bad = dict(config, nodes=[config['nodes'][0], dict(config['nodes'][0], id='duplicate')])
try:
    module.build_payload(bad)
    raise AssertionError('Duplicate endpoint accepted')
except ValueError:
    pass

with tempfile.TemporaryDirectory(prefix='morok-proxy-test-') as directory:
    work = Path(directory)
    private = work / 'TEST-ONLY-key.pem'
    public = work / 'public.der'
    subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)],
                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-outform', 'DER', '-out', str(public)], check=True)
    source = work / 'input.json'; source.write_text(json.dumps(config))
    envelope = work / 'pool.txt'
    subprocess.run([os.environ.get('MOROK_PYTHON', 'python3'), str(root / 'infra/sign-pool.py'), '--input', str(source),
                    '--private-key', str(private), '--key-id', 'test', '--output', str(envelope)], check=True)
    subprocess.run([os.environ.get('MOROK_JAVAC', 'javac'), '-encoding', 'UTF-8', '-d', str(work),
                    str(root / 'TMessagesProj/src/main/java/org/morok/proxy/ProxyNode.java'),
                    str(root / 'TMessagesProj/src/main/java/org/morok/proxy/SignedProxyPool.java'),
                    str(root / 'infra/tests/PoolEnvelopeVerifier.java')], check=True)
    subprocess.run([os.environ.get('MOROK_JAVA', 'java'), '-cp', str(work), 'PoolEnvelopeVerifier', str(envelope), str(public)], check=True)
