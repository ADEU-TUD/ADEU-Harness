package de.lokal.whisper;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Speichert Aufnahmen platzsparend: Opus in Ogg (ca. 11 MB pro Stunde), sonst AAC in M4A (ca. 22 MB),
 * statt unkomprimiertem WAV (115 MB pro Stunde). Eingabe: 16 kHz mono PCM16.
 */
final class AudioEncoder {
    private AudioEncoder() {}

    static String encode(File pcm, File dir, String base) {
        String[][] variants = {
            {MediaFormat.MIMETYPE_AUDIO_OPUS, ".ogg", "24000", String.valueOf(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)},
            {MediaFormat.MIMETYPE_AUDIO_AAC, ".m4a", "40000", String.valueOf(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)},
        };
        for (String[] v : variants) {
            File out = new File(dir, base + v[1]);
            try {
                encode(pcm, out, v[0], Integer.parseInt(v[2]), Integer.parseInt(v[3]));
                if (out.length() > 0) return out.getName();
            } catch (Throwable t) {
                Util.err("encode " + v[0], t);
            }
            out.delete();
        }
        return null;
    }

    private static void encode(File pcm, File out, String mime, int bitrate, int muxerFormat) throws Exception {
        MediaFormat fmt = MediaFormat.createAudioFormat(mime, 16000, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)) fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(pcm), 1 << 16)) {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            muxer = new MediaMuxer(out.getAbsolutePath(), muxerFormat);
            int track = -1;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] chunk = new byte[4096];
            long samplesIn = 0;
            boolean inputDone = false, outputDone = false;
            while (!outputDone) {
                if (!inputDone) {
                    int idx = codec.dequeueInputBuffer(10000);
                    if (idx >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(idx);
                        int want = Math.min(chunk.length, ib.remaining()) & ~1;
                        int n = readFully(in, chunk, want);
                        long pts = samplesIn * 1_000_000L / 16000;
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            ib.put(chunk, 0, n);
                            codec.queueInputBuffer(idx, 0, n, pts, 0);
                            samplesIn += n / 2;
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 10000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (o >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(o);
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (ob != null && info.size > 0 && !config && muxerStarted) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        muxer.writeSampleData(track, ob, info);
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } finally {
            try { codec.stop(); } catch (Throwable ignored) {}
            codec.release();
            if (muxer != null) {
                try { if (muxerStarted) muxer.stop(); } catch (Throwable ignored) {}
                try { muxer.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private static int readFully(InputStream in, byte[] b, int len) throws java.io.IOException {
        int got = 0;
        while (got < len) {
            int n = in.read(b, got, len - got);
            if (n < 0) break;
            got += n;
        }
        return got;
    }
}
