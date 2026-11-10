#!/usr/bin/env python3
"""Prepare the pinned Robolectric Android 15 runtime before tests go offline."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import urllib.request

VERSION = "15-robolectric-12650502-i7"
NAME = f"android-all-instrumented-{VERSION}.jar"
RELATIVE = f"org/robolectric/android-all-instrumented/{VERSION}/{NAME}"
ROOT = Path(os.environ["CLIPFORGE_ROBOLECTRIC_SDK"])
ROOT.mkdir(parents=True, exist_ok=True)
TARGET = ROOT / NAME
CHECKSUM = ROOT / (NAME + ".sha512")

def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha512").hexdigest()

def valid_checksum(value):
    return bool(re.fullmatch(r"[0-9a-fA-F]{128}", value))

if TARGET.is_file() and CHECKSUM.is_file():
    expected = CHECKSUM.read_text().strip()
    if valid_checksum(expected) and digest(TARGET) == expected.lower():
        print("Verified cached Robolectric Android 15 SDK")
        raise SystemExit(0)

for base in (
    "https://maven-central.storage-download.googleapis.com/maven2",
    "https://repo1.maven.org/maven2",
    "https://repo.maven.apache.org/maven2",
):
    partial = TARGET.with_suffix(".partial")
    try:
        with urllib.request.urlopen(f"{base}/{RELATIVE}.sha512", timeout=30) as response:
            expected = response.read(2048).decode("ascii").strip().split()[0]
        if not valid_checksum(expected):
            raise ValueError("Invalid published SHA-512")
        with urllib.request.urlopen(f"{base}/{RELATIVE}", timeout=60) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output)
        if digest(partial) != expected.lower():
            raise ValueError("SDK checksum mismatch")
        partial.replace(TARGET)
        CHECKSUM.write_text(expected.lower())
        print("Downloaded and SHA-512 verified pinned Robolectric Android 15 SDK")
        break
    except Exception as error:
        partial.unlink(missing_ok=True)
        print(f"SDK mirror unavailable ({type(error).__name__}): {error}")
else:
    raise SystemExit("Could not fetch a verified Robolectric SDK from any mirror")
