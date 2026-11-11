# Studio v1.6.0 Build 136 checkpoint

APK source: c78ceb0b4dde95a21decd892753306ea8525b161.
Branch: feature/studio-upgrade. Original implementation: e782cdaead830f738d45d00098315a6682e95f9b.
APK SHA-256: 90834cf61f87a9c548ef848c606ad5afbf1c58d8244a1304ce55325eebbe4eed.
Size: 17,683,342 bytes. Non-debuggable; original signing identity; ARM64 + x86_64.

Actual completed checks:
- 188 unit/Compose tests, zero failures/errors/skipped.
- 240 native Whisper endurance windows, including reload.
- 16 Android media integration tests, zero failures/errors/skipped.
- Release signing identity, optimized inference flags, manifest and checksum verified.

APK build run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43058346187
Immutable acceptance/media verification: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43061309695
Verification source: 5e86acb93bacfaf436dc712a0c2d8c33c043d522 on verify/studio-apk.

Full actual signed-APK acceptance is still running at this checkpoint. Do not report it passed until its result.json confirms success. Physical Nubia Neo 2 testing remains pending.

Fixed: full build branch trigger, campaign preset hook-tone restoration, new SFX-tab test navigation, release version 1.6.0, focused new regression tests. Ubuntu-mirror setup failures were isolated before app tests and replaced with standalone FFmpeg plus a Python environment. The verifier checks the unchanged Build 136 checksum before installing it. This documentation/CI-tools checkpoint does not alter the installer.
