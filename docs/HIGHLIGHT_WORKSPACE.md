# Highlight workspace fix — 1.8.1 candidate

The editor Back action returns to the saved shortlist. An explicit Back to highlights button and Open saved highlights entry let the user revisit results without running detection again.

Each shortlist entry opens a stable separate catalog project. Switching saves the current clip first; reopening restores its trim, captions, effects, SFX and music. The parent retains all 15 candidates. Matching legacy timeline cuts are adopted by source and original bounds when possible.

Select individual highlights or Select all, explicitly confirm review, then approve and queue up to 15 separate MP4 snapshots. Export starts only from the approved queue. Restoring an interrupted queue requires review again. The batch is rejected before writes if queue capacity is insufficient.

Added 12 workspace/queue regression tests and 2 Compose controls tests. Execution and APK availability require a successful CI runner. These additions are not yet claimed to pass, and are not present in the previously delivered 1.8.0 APK.
