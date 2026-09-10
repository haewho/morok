#!/usr/bin/env python3
import base64
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
SIGNER = ROOT / 'infra/sign-update.py'

spec = importlib.util.spec_from_file_location('sign_update', SIGNER)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

with tempfile.TemporaryDirectory(prefix='morok-update-signer-test-') as temp_name:
    temp = Path(temp_name)
    key = temp / 'key.pem'
    public = temp / 'public.pem'
    apk = temp / 'app.apk'
    metadata = temp / 'update.json'
    envelope_path = temp / 'update.txt'
    apk.write_bytes(b'MOROK-TEST-APK\n' + b'x' * (1024 * 1024))
    now = int(time.time())
    config = {
        'version_code': 70409,
        'version_name': '12.10.2',
        'package': 'io.github.haewho.morok',
        'abi': 'arm64-v8a',
        'issued': now - 5,
        'expires': now + 86400,
        'url': 'https://updates.example/MOROK.apk',
        'certificate_sha256': 'b' * 64,
        'telegram_base': '12.10.1-7038',
        'commit': '6a63205a86ea3d04ab4191479a93bfd6a5458294',
        'changelog': 'Проверяемое обновление\nБез тихой установки',
    }
    metadata.write_text(json.dumps(config))
    subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                    '-out', str(key)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    subprocess.run(['openssl', 'pkey', '-in', str(key), '-pubout', '-out', str(public)],
                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    subprocess.run(['python3', str(SIGNER), '--input', str(metadata), '--apk', str(apk),
                    '--private-key', str(key), '--key-id', 'test-key', '--output', str(envelope_path)],
                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    lines = envelope_path.read_bytes().split(b'\n')
    assert lines[0] == b'MOROK-SIGNED-UPDATE-1' and lines[1] == b'test-key' and lines[4] == b''
    payload = base64.b64decode(lines[3], validate=True)
    signature = base64.b64decode(lines[2], validate=True)
    payload_path = temp / 'payload'
    signature_path = temp / 'signature'
    payload_path.write_bytes(payload)
    signature_path.write_bytes(signature)
    subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(public), '-signature', str(signature_path),
                    str(payload_path)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    text = payload.decode('utf-8')
    assert 'package\tio.github.haewho.morok\n' in text
    assert 'size\t' + str(apk.stat().st_size) + '\n' in text
    assert 'changelog_b64\t' + base64.b64encode(config['changelog'].encode()).decode() + '\n' in text
    assert module.build_payload(config, apk) == payload

print('PASS: Python/OpenSSL update signer emits the strict verified envelope')
