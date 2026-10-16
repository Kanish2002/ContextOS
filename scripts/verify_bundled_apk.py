#!/usr/bin/env python3
"""Verify that an Android APK actually contains the complete model (not a stub)."""
import hashlib
import sys
import zipfile

EXPECTED = '2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139'
NAME = 'assets/models/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm'


def verify(path):
    with zipfile.ZipFile(path) as apk:
        entry = apk.getinfo(NAME)
        assert entry.file_size > 300_000_000, f'Bundled model unexpectedly small: {entry.file_size}'
        digest = hashlib.sha256()
        with apk.open(entry) as source:
            for block in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(block)
        assert digest.hexdigest() == EXPECTED, 'APK model SHA-256 mismatch'
        print(f'VALID: {path}, embedded model {entry.file_size:,} bytes, sha256={digest.hexdigest()}')


if __name__ == '__main__':
    verify(sys.argv[1])
