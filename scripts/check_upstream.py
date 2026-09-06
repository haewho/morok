#!/usr/bin/env python3
"""Check pinned source ancestry and every submodule, without changing refs."""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], text=True).strip()

def main():
    lock = json.loads((ROOT / 'upstream.lock.json').read_text())
    if subprocess.run(['git', '-C', str(ROOT), 'merge-base', '--is-ancestor', lock['commit'], 'HEAD']).returncode:
        raise ValueError('Current branch does not contain the pinned Telegram commit.')
    for path, expected in lock['submodules'].items():
        actual = git('-C', str(ROOT / path), 'rev-parse', 'HEAD')
        if actual != expected:
            raise ValueError(f'{path}: expected {expected}, got {actual}; initialize/update submodules.')
        if git('-C', str(ROOT / path), 'status', '--porcelain', '--untracked-files=no'):
            raise ValueError(f'{path}: modified tracked source; review before building.')
    print(f"Pinned Telegram {lock['versionName']}: {lock['commit']}; {len(lock['submodules'])} submodules verified.")

if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
