package com.clipforge.mobile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioSystem;

/** Public-sample integration test of the production JNI bridge, without Android UI. */
public final class WhisperNative {
    interface ProgressListener { void onProgress(int percent); }
    native String transcribeSegmentsNative(String model, float[] audio, ProgressListener listener);
    native void releaseModelNative();
    native void configureLanguageNative(String language);

    public static void main(String[] args) throws Exception {
        System.loadLibrary("clipforge_whisper");
        float[] samples;
        try (var input = AudioSystem.getAudioInputStream(new File(args[1]))) {
            var format = input.getFormat();
            if (format.getChannels() != 1 || format.getSampleRate() != 16000 || format.getSampleSizeInBits() != 16 || format.isBigEndian())
                throw new AssertionError("Expected mono PCM16 16 kHz sample");
            byte[] pcm = input.readAllBytes();
            samples = new float[pcm.length / 2];
            for (int i = 0; i < samples.length; i++)
                samples[i] = (short)((pcm[i * 2] & 255) | ((pcm[i * 2 + 1] & 255) << 8)) / 32768f;
        }
        var bridge = new WhisperNative();
        var callbacks = new AtomicInteger();
        var invalidCallbacks = new AtomicInteger();
        ProgressListener listener = percent -> {
            if (percent < 0 || percent > 100) { invalidCallbacks.incrementAndGet(); return; }
            callbacks.incrementAndGet();
        };
        try {
            for (int pass = 0; pass < 2; pass++) {
                long started = System.nanoTime();
                bridge.configureLanguageNative(pass == 0 ? "en" : "auto");
                String result = bridge.transcribeSegmentsNative(args[0], samples, listener);
                if (result == null || result.startsWith("ERROR:") || !result.contains("\t"))
                    throw new AssertionError("Inference did not return timed speech: " + result);
                if (!result.toLowerCase(java.util.Locale.ROOT).contains("country"))
                    throw new AssertionError("Public JFK sample was not recognized");
                System.out.printf("Native smoke pass %d: timed speech recognized in %.2fs%n", pass + 1,
                    (System.nanoTime() - started) / 1_000_000_000.0);
            }
        } finally {
            bridge.releaseModelNative();
            bridge.releaseModelNative(); // Cleanup must be idempotent.
        }
        if (callbacks.get() == 0) throw new AssertionError("No native progress callbacks received");
        System.out.println("Native progress callbacks received: " + callbacks.get());
        // A new session must be able to reload after cleanup.
        try {
            String reloaded = bridge.transcribeSegmentsNative(args[0], samples, listener);
            if (reloaded == null || !reloaded.toLowerCase(java.util.Locale.ROOT).contains("country"))
                throw new AssertionError("Reload after session cleanup failed");
        } finally { bridge.releaseModelNative(); }
        if (invalidCallbacks.get() != 0) throw new AssertionError("Invalid native progress values");
        System.out.println("Native model reuse, cleanup, reload and progress smoke test passed");
        int windows = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        if (windows > 0) endurance(bridge, args[0], samples, listener, callbacks, invalidCallbacks, windows);
    }

    private static long memoryKiB(String field) throws Exception {
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            return lines.filter(line -> line.startsWith(field + ":")).findFirst()
                .map(line -> Long.parseLong(line.trim().split("\\s+")[1])).orElseThrow();
        }
    }

    private static void endurance(WhisperNative bridge, String model, float[] speech, ProgressListener listener,
            AtomicInteger callbacks, AtomicInteger invalidCallbacks, int windows) throws Exception {
        if (windows != 240) throw new AssertionError("Expected the complete 240-window workload");
        float[] window = new float[30 * 16000];
        System.arraycopy(speech, 0, window, 0, Math.min(speech.length, window.length));
        long started = System.nanoTime();
        long warmMemory = 0;
        long endMemory = 0;
        Path report = Path.of("build/native-smoke/endurance-report.json");
        try {
            for (int index = 0; index < windows; index++) {
                if (index == 120) bridge.releaseModelNative();
                int before = callbacks.get();
                String result = bridge.transcribeSegmentsNative(model, window, listener);
                if (result == null || result.startsWith("ERROR:") ||
                        !result.toLowerCase(Locale.ROOT).contains("country"))
                    throw new AssertionError("Speech missing in endurance window " + index);
                if (callbacks.get() <= before || invalidCallbacks.get() != 0)
                    throw new AssertionError("Progress failed in endurance window " + index);
                int usableCues = 0;
                for (String cue : result.split("\\n")) {
                    if (cue.isBlank()) continue;
                    String[] parts = cue.split("\\t", 3);
                    if (parts.length != 3) throw new AssertionError("Malformed timed cue");
                    long from = Long.parseLong(parts[0]), until = Long.parseLong(parts[1]);
                    // Match the production Kotlin wrapper's clipping/filtering semantics.
                    from = Math.max(0, from); until = Math.min(30000, until);
                    if (until > from && !parts[2].isBlank()) usableCues++;
                }
                if (usableCues == 0) throw new AssertionError("No usable timed cues");
                if (index == 9) warmMemory = memoryKiB("VmRSS");
                endMemory = memoryKiB("VmRSS");
                if ((index + 1) % 20 == 0) {
                    double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
                    String json = String.format(Locale.ROOT,
                        "{\"completed_windows\":%d,\"processed_audio_ms\":%d,\"wall_seconds\":%.3f," +
                        "\"rss_warm_kib\":%d,\"rss_end_kib\":%d,\"rss_peak_kib\":%d," +
                        "\"synthetic_input\":\"JFK public sample padded to 30s, repeated 240 times\"," +
                        "\"platform\":\"Linux x86_64 CI, not a phone benchmark\"}%n",
                        index + 1, (index + 1) * 30000L, elapsed, warmMemory, endMemory, memoryKiB("VmHWM"));
                    Files.writeString(report, json);
                    System.out.printf(Locale.ROOT, "Endurance %d/%d windows in %.1fs; RSS %d KiB%n",
                        index + 1, windows, elapsed, endMemory);
                }
            }
            if (endMemory > warmMemory + 128 * 1024)
                throw new AssertionError("Endurance retained more than 128 MiB after warmup");
        } finally { bridge.releaseModelNative(); }
        System.out.println("Two-hour-equivalent real native inference workload passed, including model reload");
    }
}
