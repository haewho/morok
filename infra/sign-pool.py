#!/usr/bin/env python3
"""Create an unambiguous signed data-only pool. Does not publish or print secrets."""
import argparse
import base64
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
from urllib.parse import urlsplit, parse_qsl


def build_payload(config):
    if set(config) != {'version', 'issued', 'expires', 'nodes'}:
        raise ValueError('Unexpected configuration fields')
    for key in ('version', 'issued', 'expires'):
        if type(config[key]) is not int or not 0 <= config[key] < 10 ** 18:
            raise ValueError('Invalid version/timestamp')
    if config['version'] < 1 or not config['issued'] < config['expires'] <= config['issued'] + 31 * 86400:
        raise ValueError('Invalid version/validity period')
    if config['expires'] <= time.time() or config['issued'] > time.time() + 300:
        raise ValueError('Expired configuration or incorrect clock')
    nodes = config['nodes']
    if not isinstance(nodes, list) or not 1 <= len(nodes) <= 50:
        raise ValueError('Expected 1 to 50 nodes')
    rows = ['MOROK-PROXY-POOL-1', 'version\t' + str(config['version']),
            'issued\t' + str(config['issued']), 'expires\t' + str(config['expires'])]
    ids, endpoints = set(), set()
    for node in nodes:
        if set(node) != {'id', 'link'} or not re.fullmatch(r'[A-Za-z0-9_-]{1,48}', node['id']) or node['id'] in ids:
            raise ValueError('Invalid or duplicate node ID')
        ids.add(node['id'])
        link = node['link']
        if not isinstance(link, str) or len(link) > 4096 or any(ch.isspace() for ch in link):
            raise ValueError('Invalid node link')
        uri = urlsplit(link)
        if uri.fragment or uri.username or uri.password:
            raise ValueError('Use a tg:// or https://t.me/ link without URL authority credentials')
        if uri.scheme == 'tg' and uri.netloc in ('proxy', 'socks') and not uri.path:
            kind = uri.netloc
        elif uri.scheme == 'https' and uri.netloc == 't.me' and uri.path in ('/proxy', '/socks'):
            kind = uri.path[1:]
        else:
            raise ValueError('Unsupported proxy link')
        pairs = parse_qsl(uri.query, keep_blank_values=True, strict_parsing=True)
        fields = dict(pairs)
        if len(fields) != len(pairs) or set(fields) - {'server', 'port', 'user', 'pass', 'secret'}:
            raise ValueError('Duplicate or unknown node fields')
        host, port = fields.get('server', ''), fields.get('port', '')
        if not re.fullmatch(r'[A-Za-z0-9.:%_-]{1,253}', host) or not port.isdecimal() or not 1 <= int(port) <= 65535:
            raise ValueError('Invalid server/port')
        endpoint = (host.lower(), int(port))
        if endpoint in endpoints:
            raise ValueError('Duplicate endpoint is not an independent backup')
        endpoints.add(endpoint)
        if kind == 'proxy':
            secret = fields.get('secret', '')
            try:
                raw = bytes.fromhex(secret) if re.fullmatch(r'[A-Fa-f0-9]+', secret) else base64.urlsafe_b64decode(secret + '=' * (-len(secret) % 4))
            except Exception as exc:
                raise ValueError('Invalid MTProxy secret') from exc
            valid = len(raw) == 16 or len(raw) == 17 and raw[0] == 0xdd
            if 17 < len(raw) <= 270 and raw[0] == 0xee:
                domain = raw[17:].decode('ascii')
                valid = bool(re.fullmatch(r'[A-Za-z0-9.-]{1,253}', domain)) and '.' in domain
            if not valid or fields.get('user') or fields.get('pass'):
                raise ValueError('Invalid MTProxy authentication')
        elif 'secret' in fields:
            raise ValueError('Mixed proxy authentication')
        if any(len(fields.get(k, '').encode('utf-8')) > 255 or '\0' in fields.get(k, '') for k in ('user', 'pass')):
            raise ValueError('Invalid SOCKS credentials')
        if fields.get('pass') and not fields.get('user'):
            raise ValueError('Missing SOCKS username')
        rows.append('node\t' + node['id'] + '\t' + link)
    payload = ('\n'.join(rows) + '\n').encode('utf-8')
    if len(payload) > 64 * 1024:
        raise ValueError('Pool payload too large')
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--private-key', required=True, type=Path)
    parser.add_argument('--key-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,48}', args.key_id):
        parser.error('Invalid key ID')
    if args.input.stat().st_size > 96 * 1024:
        parser.error('Input is too large')
    payload = build_payload(json.loads(args.input.read_text()))
    public_der = subprocess.run(['openssl', 'pkey', '-in', str(args.private_key), '-pubout', '-outform', 'DER'],
                                check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    public_info = subprocess.run(['openssl', 'rsa', '-pubin', '-inform', 'DER', '-text', '-noout'], input=public_der,
                                 check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.decode('ascii')
    size = re.search(r'\((\d+) bit\)', public_info)
    if size is None or int(size.group(1)) < 2048:
        raise ValueError('An RSA key of at least 2048 bits is required')
    with tempfile.TemporaryDirectory(prefix='morok-pool-') as temp:
        source = Path(temp) / 'payload'
        source.write_bytes(payload)
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(args.private_key), str(source)],
                                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    envelope = b'MOROK-SIGNED-POOL-1\n' + args.key_id.encode('ascii') + b'\n' + base64.b64encode(signature) + b'\n' + base64.b64encode(payload) + b'\n'
    if len(envelope) > 96 * 1024:
        raise ValueError('Pool envelope too large')
    args.output.write_bytes(envelope)
    print('Signed pool written. Validate in the client before deployment.')


if __name__ == '__main__':
    main()
