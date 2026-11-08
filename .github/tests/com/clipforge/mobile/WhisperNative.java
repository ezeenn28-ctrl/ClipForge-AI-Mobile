package com.clipforge.mobile;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioSystem;

/** Public-sample integration test of the production JNI bridge, without Android UI. */
public final class WhisperNative {
    interface ProgressListener { void onProgress(int percent); }
    native String transcribeSegmentsNative(String model, float[] audio, ProgressListener listener);
    native void releaseModelNative();

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
        ProgressListener listener = percent -> {
            if (percent < 0 || percent > 100) throw new AssertionError("Invalid progress: " + percent);
            callbacks.incrementAndGet();
        };
        try {
            for (int pass = 0; pass < 2; pass++) {
                long started = System.nanoTime();
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
        System.out.println("Native model reuse, cleanup, reload and progress smoke test passed");
    }
}
