#!/usr/bin/env python3
"""Inspect spacing between APK ZIP entries without extracting their contents."""
from pathlib import Path
import struct
import zipfile


LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
LOCAL_HEADER_MAGIC = 0x04034B50


def max_inter_entry_gap(apk: Path) -> int:
    """Return the largest byte gap between consecutive local ZIP entries."""
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as stream:
        entries = sorted(archive.infolist(), key=lambda entry: entry.header_offset)
        largest = 0
        for current, following in zip(entries, entries[1:]):
            stream.seek(current.header_offset)
            header = stream.read(LOCAL_HEADER.size)
            if len(header) != LOCAL_HEADER.size:
                raise ValueError(f"Truncated local ZIP header for {current.filename}")
            values = LOCAL_HEADER.unpack(header)
            if values[0] != LOCAL_HEADER_MAGIC:
                raise ValueError(f"Invalid local ZIP header for {current.filename}")
            name_length, extra_length = values[-2:]
            data_end = (current.header_offset + LOCAL_HEADER.size + name_length
                        + extra_length + current.compress_size)
            gap = following.header_offset - data_end
            if gap < 0:
                raise ValueError(f"Overlapping ZIP entries near {current.filename}")
            largest = max(largest, gap)
        return largest
