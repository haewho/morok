#!/usr/bin/env python3
"""Build and RSA-sign strict MOROK update metadata. Does not publish files or print secrets."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
from urllib.parse import urlsplit


FIELDS = {'version_code', 'version_name', 'package', 'abi', 'issued', 'expires', 'url',
          'certificate_sha256', 'telegram_base', 'commit', 'changelog'}


def build_payload(config, apk):
    if set(config) != FIELDS:
        raise ValueError('Unexpected update fields')
    if type(config['version_code']) is not int or not 1 <= config['version_code'] < 10 ** 18:
        raise ValueError('Invalid version code')
    for key, pattern in (
            ('version_name', r'[A-Za-z0-9._+-]{1,48}'),
            ('package', r'[A-Za-z][A-Za-z0-9_.]{2,159}'),
            ('abi', r'arm64-v8a'),
            ('certificate_sha256', r'[0-9a-f]{64}'),
            ('telegram_base', r'[A-Za-z0-9._+-]{1,64}'),
            ('commit', r'[0-9a-f]{7,40}')):
        if not isinstance(config[key], str) or not re.fullmatch(pattern, config[key]):
            raise ValueError('Invalid ' + key)
    for key in ('issued', 'expires'):
        if type(config[key]) is not int or not 0 <= config[key] < 10 ** 18:
            raise ValueError('Invalid timestamp')
    if not config['issued'] < config['expires'] <= config['issued'] + 31 * 86400:
        raise ValueError('Invalid validity period')
    if config['expires'] <= time.time() or config['issued'] > time.time() + 300:
        raise ValueError('Expired manifest or incorrect clock')
    uri = urlsplit(config['url'])
    if uri.scheme != 'https' or not uri.hostname or uri.username or uri.password or uri.fragment or len(config['url']) > 2048:
        raise ValueError('APK URL must be owner-controlled HTTPS without credentials or fragment')
    changelog = config['changelog']
    if not isinstance(changelog, str) or '\0' in changelog or len(changelog.encode('utf-8')) > 8192:
        raise ValueError('Invalid changelog')
    size = apk.stat().st_size
    if not 1024 * 1024 <= size <= 512 * 1024 * 1024:
        raise ValueError('APK size is outside client limits')
    digest = hashlib.sha256()
    with apk.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    rows = [
        'MOROK-UPDATE-1',
        'version_code\t' + str(config['version_code']),
        'version_name\t' + config['version_name'],
        'package\t' + config['package'],
        'abi\t' + config['abi'],
        'issued\t' + str(config['issued']),
        'expires\t' + str(config['expires']),
        'size\t' + str(size),
        'sha256\t' + digest.hexdigest(),
        'certificate_sha256\t' + config['certificate_sha256'],
        'url\t' + config['url'],
        'telegram_base\t' + config['telegram_base'],
        'commit\t' + config['commit'],
        'changelog_b64\t' + base64.b64encode(changelog.encode('utf-8')).decode('ascii'),
    ]
    payload = ('\n'.join(rows) + '\n').encode('utf-8')
    if len(payload) > 32 * 1024:
        raise ValueError('Update payload too large')
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--private-key', required=True, type=Path)
    parser.add_argument('--key-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,48}', args.key_id):
        parser.error('Invalid key ID')
    if args.input.stat().st_size > 32 * 1024:
        parser.error('Input is too large')
    payload = build_payload(json.loads(args.input.read_text()), args.apk)
    public_der = subprocess.run(['openssl', 'pkey', '-in', str(args.private_key), '-pubout', '-outform', 'DER'],
                                check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    public_info = subprocess.run(['openssl', 'rsa', '-pubin', '-inform', 'DER', '-text', '-noout'], input=public_der,
                                 check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.decode('ascii')
    size = re.search(r'\((\d+) bit\)', public_info)
    if size is None or int(size.group(1)) < 2048:
        raise ValueError('An RSA key of at least 2048 bits is required')
    with tempfile.TemporaryDirectory(prefix='morok-update-') as temp:
        source = Path(temp) / 'payload'
        source.write_bytes(payload)
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(args.private_key), str(source)],
                                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    envelope = (b'MOROK-SIGNED-UPDATE-1\n' + args.key_id.encode('ascii') + b'\n'
                + base64.b64encode(signature) + b'\n' + base64.b64encode(payload) + b'\n')
    if len(envelope) > 48 * 1024:
        raise ValueError('Update envelope too large')
    args.output.write_bytes(envelope)
    print('Signed update manifest written. Verify it in MOROK before publication.')


if __name__ == '__main__':
    main()
