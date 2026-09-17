#!/usr/bin/env python3
"""Remove only final APK outputs so Android packaging cannot retain incremental ZIP holes."""
from pathlib import Path
import sys


def prepare(build_root: Path, variant: str) -> int:
    if variant not in ("debug", "release"):
        raise ValueError("variant must be debug or release")
    removed = 0
    for directory in (
        build_root / "outputs" / "apk" / "afat" / variant,
        build_root / "intermediates" / "apk" / "afat" / variant,
    ):
        if not directory.is_dir():
            continue
        for candidate in directory.glob("*.apk"):
            if candidate.is_file() or candidate.is_symlink():
                candidate.unlink()
                removed += 1
    return removed


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_apk_output.py <app-build-dir> <debug|release>")
    try:
        removed = prepare(Path(sys.argv[1]), sys.argv[2])
    except ValueError as error:
        raise SystemExit(str(error))
    print(f"Prepared compact APK output ({removed} stale file(s) removed).")


if __name__ == "__main__":
    main()
