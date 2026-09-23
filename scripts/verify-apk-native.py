"""Verify ARM64 ELF page alignment and JNI exports without executing native code."""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk")
exports = {}
with zipfile.ZipFile(apk) as archive:
    libraries = [name for name in archive.namelist() if name.endswith(".so")]
    assert libraries, "No native libraries in APK"
    for name in libraries:
        assert name.startswith("lib/arm64-v8a/"), f"Unexpected ABI: {name}"
        data = archive.read(name)
        assert data[:6] == b"\x7fELF\x02\x01", f"Not little-endian ELF64: {name}"
        header = struct.unpack_from("<HHIQQQIHHHHHH", data, 16)
        assert header[1] == 183, f"Not AArch64: {name}"
        for index in range(header[9]):
            segment = struct.unpack_from("<IIQQQQQQ", data, header[4] + index * header[8])
            if segment[0] == 1:
                assert segment[7] >= 16384 and (segment[3] - segment[2]) % 16384 == 0, f"Not 16 KB compatible: {name}"
        sections = [struct.unpack_from("<IIQQQQIIQQ", data, header[5] + index * header[10]) for index in range(header[11])]
        names = []
        for section in sections:
            if section[1] != 11:
                continue
            strings = sections[section[6]]
            table = data[strings[4]:strings[4] + strings[5]]
            for offset in range(section[4], section[4] + section[5], section[9]):
                symbol = struct.unpack_from("<IBBHQQ", data, offset)
                if symbol[3] and symbol[1] >> 4 in (1, 2):
                    end = table.find(b"\x00", symbol[0])
                    names.append(table[symbol[0]:end].decode("utf-8", errors="replace"))
        exports[Path(name).name] = names
    for library in ("libai-chat.so", "libwhisper.so", "libcoder-wan.so"):
        count = sum(name.startswith("Java_") for name in exports.get(library, []))
        assert count > 0, f"JNI exports missing: {library}"
        print(f"{library}: {count} JNI exports")
print(f"{len(libraries)} ARM64 libraries validated for 16 KB ELF alignment")
print(f"APK bytes: {apk.stat().st_size}")
with apk.open("rb") as stream:
    print(f"SHA-256: {hashlib.file_digest(stream, 'sha256').hexdigest()}")
