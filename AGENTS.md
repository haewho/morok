# MOROK development

MOROK is an Android fork of the official Telegram client. The approved long-term requirements are in `docs/REQUIREMENTS.md`; implementation status is in `docs/PROGRESS.md` and `docs/FEATURE_PARITY.md`. A requirement is not proof of implementation.

## Commands

- `./scripts/build.sh debug` — arm64 development APK, no borrowed app credentials.
- `./scripts/build.sh release` — validates the owner's API credentials and persistent release key.
- `./scripts/check.sh` — portable MOROK tests and pinned-source checks.
- `./gradlew morokPreflight` — readiness without printing secrets.
- `./scripts/prepare-upstream-update.sh <verified-ref-or-sha>` — creates an isolated upgrade branch/worktree from a clean working tree.

Read `docs/BUILD.md` for the pinned toolchain. Use the existing Gradle wrapper. On 8 GB hosts retain 2 workers and the 3 GB heap. Do not run two full Android builds simultaneously.

## Invariants

Keep the official tree, protocol, JNI names and ordinary messenger behavior. Own logic lives in `org.morok`. Every hook needs a symbol, ordering/thread invariant and test entry in `docs/HOOKS.md`. Preserve incoming delete/edit updates, pts/qts/seq and transport acknowledgements. Archived data is a separate local projection.

Scope local data by stable authorized user ID, never reusable account slot alone. Exclude secret, ephemeral, one-time and no-forwards content. Respect logout, user deletion tombstones, failed writes and missing keys. Never recover unavailable originals from thumbnails or report untested guarantees.

`origin` is `haewho/morok`; `upstream` is `DrKLO/Telegram`. Keep upstream ancestry and small reviewable feature commits. Read working-tree status before edits, preserve others' changes and avoid force-push. Update locks only after reviewing a real upstream change; a conflict-free merge is not a semantic hook audit.

Never commit local API/Firebase/proxy/signing credentials or use Telegram's public demo identity. Test-only unauthenticated builds must retain a clear login explanation. Keep device, compilation, account and Russian-carrier checks separate. Do not mark the whole P0 complete after a scaffold or successful compile.
