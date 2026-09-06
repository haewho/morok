#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# != 1 ]]; then
    printf '%s\n' 'Usage: scripts/prepare-upstream-update.sh <verified-upstream-ref-or-sha>' >&2
    exit 2
fi
if [[ -n "$(git status --porcelain)" ]]; then
    printf '%s\n' 'Commit or preserve current changes before preparing an upgrade.' >&2
    exit 1
fi
morok_url="$(git remote get-url upstream)"
if [[ "$morok_url" != 'https://github.com/DrKLO/Telegram.git' ]]; then
    printf '%s\n' 'Unexpected upstream URL; review it before fetching.' >&2
    exit 1
fi
git fetch upstream "$1"
morok_sha="$(git rev-parse FETCH_HEAD)"
morok_branch="codex/upgrade-$(date +%Y%m%d)-${morok_sha:0:8}"
morok_worktree="../morok-upgrade-${morok_sha:0:8}"
git worktree add -b "$morok_branch" "$morok_worktree" HEAD
printf 'Prepared %s at %s.\n' "$morok_branch" "$morok_worktree"
printf 'Review commit %s and docs/UPDATING.md, then merge upstream in that worktree.\n' "$morok_sha"
