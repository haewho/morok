#!/usr/bin/env python3
from pathlib import Path
import importlib.util
import struct
import tempfile
import zipfile

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("prepare_apk_output", root / "scripts/prepare_apk_output.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
layout_spec = importlib.util.spec_from_file_location("apk_zip_layout", root / "scripts/apk_zip_layout.py")
layout = importlib.util.module_from_spec(layout_spec)
layout_spec.loader.exec_module(layout)
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


with tempfile.TemporaryDirectory(prefix="morok-apk-output-") as temp:
    build = Path(temp) / "build"
    output_debug = build / "outputs/apk/afat/debug"
    intermediate_debug = build / "intermediates/apk/afat/debug"
    output_release = build / "outputs/apk/afat/release"
    for directory in (output_debug, intermediate_debug, output_release):
        directory.mkdir(parents=True)
    (output_debug / "old.apk").write_bytes(b"stale-output")
    (intermediate_debug / "old.apk").write_bytes(b"stale-intermediate")
    (intermediate_debug / "output-metadata.json").write_text("{}")
    (output_release / "release.apk").write_bytes(b"keep-release")

    expect(module.prepare(build, "debug") == 2,
           "Both known debug APK outputs are removed")
    expect(not (output_debug / "old.apk").exists()
           and not (intermediate_debug / "old.apk").exists(),
           "Stale debug APK files cannot reach incremental packaging")
    expect((intermediate_debug / "output-metadata.json").exists()
           and (output_release / "release.apk").read_bytes() == b"keep-release",
           "Metadata and the other variant remain untouched")
    expect(module.prepare(build, "debug") == 0,
           "Preparation is idempotent when no APK exists")
    rejected = False
    try:
        module.prepare(build, "benchmark")
    except ValueError:
        rejected = True
    expect(rejected, "Unknown variants fail closed")

    compact = Path(temp) / "compact.apk"
    with zipfile.ZipFile(compact, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("first", b"a")
        archive.writestr("second", b"b")
    expect(layout.max_inter_entry_gap(compact) <= 24,
           "A normally packaged APK has no excessive inter-entry gap")

    padded = Path(temp) / "padded.apk"
    blob = bytearray(compact.read_bytes())
    with zipfile.ZipFile(compact) as archive:
        second_offset = archive.getinfo("second").header_offset
    eocd_offset = blob.rfind(b"PK\x05\x06")
    central_offset = struct.unpack_from("<I", blob, eocd_offset + 16)[0]
    padding = b"\0" * (70 * 1024)
    blob[second_offset:second_offset] = padding
    new_central_offset = central_offset + len(padding)
    new_eocd_offset = eocd_offset + len(padding)
    struct.pack_into("<I", blob, new_eocd_offset + 16, new_central_offset)
    cursor = new_central_offset
    while blob[cursor:cursor + 4] == b"PK\x01\x02":
        local_offset = struct.unpack_from("<I", blob, cursor + 42)[0]
        if local_offset >= second_offset:
            struct.pack_into("<I", blob, cursor + 42, local_offset + len(padding))
        name_length, extra_length, comment_length = struct.unpack_from("<HHH", blob, cursor + 28)
        cursor += 46 + name_length + extra_length + comment_length
    padded.write_bytes(blob)
    expect(layout.max_inter_entry_gap(padded) == len(padding),
           "The layout audit detects a large incremental packaging hole")

print(f"APK output preparation: {checks} checks passed")
