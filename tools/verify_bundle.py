#!/usr/bin/env python3
"""Verify every packaged file against the included SHA-256 inventory."""
from pathlib import Path, PurePosixPath
import hashlib, sys
root=Path(__file__).resolve().parents[1]
manifest=root/'SHA256SUMS.txt'
failed=[]
expected=set()
for line in manifest.read_text(encoding='utf-8').splitlines():
    digest,name=line.split('  ',1)
    relative=PurePosixPath(name)
    if relative.is_absolute() or '..' in relative.parts:
        failed.append('Unsafe manifest path: '+name)
        continue
    expected.add(name)
    file=root.joinpath(*relative.parts)
    if not file.is_file():
        failed.append('Missing: '+name)
    elif hashlib.sha256(file.read_bytes()).hexdigest()!=digest:
        failed.append('Checksum mismatch: '+name)
def skip_part(parts):
    return any(x in skip for x in parts)
skip=('.git','.github','private','references','archives','node_modules','build','dist','signing')
actual={p.relative_to(root).as_posix() for p in root.rglob('*')
        if p.is_file() and p!=manifest and not skip_part(p.relative_to(root).parts)}
for name in sorted(actual-expected):
    failed.append('Unlisted file: '+name)
if failed:
    print('\n'.join(failed))
    sys.exit(1)
print('Verified '+str(len(expected))+' files: no missing files or SHA-256 mismatches.')
