#!/usr/bin/env python3
"""Opt-in cache of pinned public IR sources. Verifies Git blob hashes; executes nothing."""
from __future__ import annotations
import argparse
import hashlib
import shutil
import sys
import tempfile
from pathlib import Path, PurePosixPath
from urllib.request import Request, urlopen
from validate_docs import ROOT, load_json


def git_blob_sha1(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode('ascii') + b'\x00' + data).hexdigest()


def safe_path(path: str) -> PurePosixPath:
    p = PurePosixPath(path)
    if p.is_absolute() or '..' in p.parts or p.suffix != '.md' or not p.parts:
        raise ValueError('unsafe source path: ' + path)
    return p


def verify_tree(folder: Path, entries) -> None:
    for entry in entries:
        p = folder / safe_path(entry['path'])
        if not p.resolve().is_relative_to(folder.resolve()):
            raise ValueError('source symlink escapes root: ' + entry['path'])
        data = p.read_bytes()
        if git_blob_sha1(data) != entry['git_blob_sha1']:
            raise ValueError('source hash mismatch: ' + entry['path'])


def cache(air, destination: Path, source: Path | None = None) -> Path:
    if air['repository'] != 'Gustavo2358/analysis-ir':
        raise ValueError('repository differs from the reviewed public source')
    commit = air['commit']
    if len(commit) != 40 or any(c not in '0123456789abcdef' for c in commit):
        raise ValueError('immutable commit required')
    entries = air['files']
    if not entries:
        raise ValueError('empty source inventory')
    for entry in entries:
        safe_path(entry['path'])
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / commit
    if target.exists():
        verify_tree(target, entries)
        return target
    staging = Path(tempfile.mkdtemp(prefix='ir-', dir=destination))
    try:
        for entry in entries:
            rel = safe_path(entry['path'])
            if source is not None:
                src = source / rel
                if not src.resolve().is_relative_to(source.resolve()):
                    raise ValueError('source path escapes selected folder')
                data = src.read_bytes()
            else:
                url = 'https://raw.githubusercontent.com/' + air['repository'] + '/' + commit + '/' + str(rel)
                request = Request(url, headers={'User-Agent': 'analysis-cfg-harness-pinned-source-cache/1'})
                with urlopen(request, timeout=25) as response:
                    data = response.read(2_000_001)
                if len(data) > 2_000_000:
                    raise ValueError('source exceeds conservative transfer limit: ' + str(rel))
            if git_blob_sha1(data) != entry['git_blob_sha1']:
                raise ValueError('source hash mismatch: ' + str(rel))
            out = staging / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
        verify_tree(staging, entries)
        staging.rename(target)
        return target
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--from-dir', type=Path, help='Copy from a local checkout of the pinned IR')
    mode.add_argument('--download', action='store_true', help='Download the reviewed public files; network required')
    args = p.parse_args()
    try:
        air = load_json(ROOT / 'docs/sources/sources.lock.json')['analysis_ir']
        dest = cache(air, ROOT / '.cache/analysis-ir', args.from_dir.resolve() if args.from_dir else None)
        print('Verified IR cache: ' + str(dest))
        return 0
    except (OSError, ValueError, KeyError) as exc:
        print('IR cache failed; no fallback to main: ' + str(exc), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
