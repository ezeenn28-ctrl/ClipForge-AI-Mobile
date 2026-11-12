# Highlight workspace fix — 1.8.1 candidate

The editor Back action returns to the saved shortlist. An explicit Back to highlights button and Open saved highlights entry let the user revisit results without running detection again.

Each shortlist entry opens a stable separate catalog project. Switching saves the current clip first; reopening restores its trim, captions, effects, SFX and music. The parent retains all 15 candidates. Matching legacy timeline cuts are adopted by source and original bounds when possible.

Select individual highlights or Select all, explicitly confirm review, then approve and queue up to 15 separate MP4 snapshots. Export starts only from the approved queue. Restoring an interrupted queue requires review again. The batch is rejected before writes if queue capacity is insufficient.

Build 148 APK is available for source 6db1bc1e657d895b0b06ca78bd274b2975f2b630. All 235 unit/Compose tests passed, including 12 workspace/queue regression tests and 2 Compose approval-control tests. Actual signed APK acceptance passed all 30 checks. Native speech integration and 240 synthetic 30-second windows passed (242.064 seconds on Linux x86_64 CI, not a phone benchmark).

The full Android media suite passed 25/26 tests; dipTransitionRendersBlackAtBoundaryAndKeepsTheOriginalDuration failed once. A focused diagnostic with unchanged release application source and the same assertion passed 1/1 and logged black RGB 0,0,0 at the boundary. The original failure remains unexplained. This is a release candidate, not an all-green release.

Release run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43234682255 (attempt 2).
Focused diagnostic: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43248886829.
APK SHA-256: 40060bcf75677c233e655544d63d6386344509f4a825dbc1f5ac35aec2a2f123.
Signing identity matches the previous installer; install as an update. The 15-output batch has unit/Compose coverage but has not been measured on the owner's physical Nubia Neo 2 5G.
