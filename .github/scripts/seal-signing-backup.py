#!/usr/bin/env python3
"""Export only an authenticated, owner-encrypted copy of the original signing key."""
import hashlib
from pathlib import Path
import subprocess

keystore = Path.home() / ".android/clipforge-development.keystore"
expected = Path(".github/signing/identity.sha256").read_text().strip()
certificate = subprocess.check_output([
    "keytool", "-exportcert", "-keystore", str(keystore), "-storepass", "android", "-alias", "androiddebugkey"
], stderr=subprocess.DEVNULL)
if hashlib.sha256(certificate).hexdigest() != expected:
    raise SystemExit("Signing certificate differs from the installed Build 94 identity")
output = Path("release-private/ClipForge-signing-backup.cms")
output.parent.mkdir(exist_ok=True)
subprocess.run([
    "openssl", "cms", "-encrypt", "-aes-256-gcm", "-binary", "-in", str(keystore),
    "-outform", "DER", "-out", str(output), "-recip", ".github/signing/owner-recovery.crt",
    "-keyopt", "rsa_padding_mode:oaep", "-keyopt", "rsa_oaep_md:sha256"
], check=True)
print("Original signing identity verified; owner-only AES-GCM/RSA-OAEP recovery envelope created")
