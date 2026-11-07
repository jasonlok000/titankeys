#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/toolchains
archive=build/toolchains/android-ndk-r27c-darwin.zip
if [ ! -f "$archive" ]; then
  curl -L --fail --show-error https://dl.google.com/android/repository/android-ndk-r27c-darwin.zip -o "$archive"
fi
shasum -a 256 -c native/ndk.sha256
python3 - <<'PY'
from pathlib import Path
import stat
from zipfile import ZipFile
root = Path('build/toolchains')
with ZipFile(root / 'android-ndk-r27c-darwin.zip') as archive:
    archive.extractall(root)
    for item in archive.infolist():
        mode = item.external_attr >> 16
        if stat.S_ISLNK(mode):
            destination = root / item.filename
            destination.unlink()
            destination.symlink_to(archive.read(item).decode())
        elif mode & 0o111:
            (root / item.filename).chmod(mode & 0o777)
PY
