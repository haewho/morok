#!/usr/bin/env python3
"""Collect a built APK and public build/signature metadata; never read credentials."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parent.parent
variant = sys.argv[1] if len(sys.argv) == 2 else 'debug'
if variant not in ('debug', 'release'):
    raise SystemExit('Usage: report_apk.py [debug|release]')
build = root / 'TMessagesProj_App/build'
candidates = list((build / f'outputs/apk/afat/{variant}').glob('*.apk'))
candidates += list((build / f'intermediates/apk/afat/{variant}').glob('*.apk'))
if not candidates:
    raise SystemExit('No assembled APK found; this is not a successful build.')
apk = max(candidates, key=lambda p: p.stat().st_mtime)
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
tools = sdk / 'build-tools/36.0.0'
badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
package_line = next(line for line in badging.splitlines() if line.startswith('package:'))
package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
expected = 'io.github.haewho.morok' + ('.beta' if variant == 'debug' else '')
if package.get('name') != expected:
    raise SystemExit('Unexpected package identity; refusing to label this APK as MOROK.')
certs = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
with zipfile.ZipFile(apk) as archive:
    abis = sorted({p.split('/')[1] for p in archive.namelist() if p.startswith('lib/') and p.endswith('.so')})
if abis != ['arm64-v8a']:
    raise SystemExit(f'Expected only arm64-v8a, found {abis}; review packaging.')
out = root / 'artifacts'
out.mkdir(exist_ok=True)
target = out / f'MOROK-{package["versionName"]}-{variant}-arm64.apk'
shutil.copy2(apk, target)
digest = hashlib.sha256(target.read_bytes()).hexdigest()
lock = json.loads((root / 'upstream.lock.json').read_text())
report = {'apk': target.name, 'sha256': digest, 'bytes': target.stat().st_size,
          'package': package, 'abis': abis, 'upstream': lock['commit'],
          'commit': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
          'workingTreeModified': bool(subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain'], text=True).strip()),
          'signature': [line for line in certs.splitlines() if not line.startswith('WARNING:')],
          'signatureDiagnostics': 'signature-verification.txt', 'deviceVerified': False, 'russianNetworkVerified': False}
(out / 'signature-verification.txt').write_text(certs)
(out / 'build-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
(out / (target.name + '.sha256')).write_text(f'{digest}  {target.name}\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
