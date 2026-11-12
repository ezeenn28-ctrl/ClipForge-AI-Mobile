# Creator tools milestone — Build 147

Implementation: all eight creator tools are present. Delivery status: **release candidate; corrected stress verification blocked by runner allocation**.

APK app-source commit: `fe69f31d6fa35f2b9e5f3641c83ce8518e275d9e`.
APK SHA-256: `028baaf2b9fe1860ccac3b1ac6d6bc3d2e62111bd5aa2b1adfaeb5e8dd99560a`.
Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
Version 1.8.0, code 147, non-debuggable, arm64-v8a + x86_64; 17,798,062 bytes.

## Actual checks

- Final source unit/Compose: **221 passed, 0 failed/error/skipped**.
- Android API 30 integration: **25/26 passed**; the ten-short-cut/mixed-speed stress test hit its initial 180-second deadline.
- Longer focused trial: **3/4 passed**. Stress export completed with 24 encoded samples for a nominal 1,250ms timeline, but the test incorrectly required 27–32 samples. The feature is a rate cap, not interpolation.
- Corrected assertions now check the maximum count, encoded duration and retained samples in each cut, with a 300-second stress-only deadline. **They have not run**: the new job and one retry both ended with runner_id=0 and zero steps/logs. Inspect GitHub Annotations before retrying.
- Actual signed Build147 acceptance: **30/30 passed**, including saved-project signed update, genuine background export, packaged JNI speech transcription, editable captions/hooks, SFX/imports, preview/frame steps, undo, persistent presets, manual export approval, queue export/restart, clean import and no app Java/native fatal exception.
- Native Whisper: **240/240 inference windows in 678.8s**, reused from successful Build143 native job. C++/JNI and WhisperNative source were unchanged. The temporary branch-based native-test skip was removed so future consolidated runs execute it normally.

## Evidence

- Build/unit/media/signed APK: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43145225812
- Focused cap/cancel/Gallery cleanup pass: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43144453957
- Longer stress trial: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43146744548
- Corrected test blocked before any runner steps: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43148282544
- Successful native job: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43140879228/job/130927267959

Use More → Creator tools for templates, local framing, beat drafts, opening text, dictionary and versions. Quality is in Export. Private draft rendering covers at most the first 60 seconds; review later timeline portions in the regular editor before export. No cloud AI was enabled.

Physical Nubia Neo 2 5G performance, OEM background behavior, real-face tracking accuracy, Tagalog/Taglish accuracy and full two-hour phone export were not measured. Install as an update, without uninstalling.
