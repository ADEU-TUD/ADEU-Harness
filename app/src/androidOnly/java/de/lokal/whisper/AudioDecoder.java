package de.lokal.whisper;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Dekodiert beliebige Audio-/Videodateien (MP3, M4A, Opus/WhatsApp, WAV, MP4 …) nach 16 kHz mono PCM16. */
final class AudioDecoder {
    private AudioDecoder() {}

    /** Gibt die Anzahl der geschriebenen 16-kHz-Samples zurueck. */
    static long decode(String inPath, File outPcm, final Platform.Progress progress, Platform.Cancel cancel) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(outPcm), 1 << 16);
        final long[] written = {0};
        try {
            ex.setDataSource(inPath);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (track < 0) throw new IOException("Keine Tonspur gefunden");
            ex.selectTrack(track);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            long durationUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 16000;
            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int encoding = 2; // PCM16
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            final byte[] pcmBytes = new byte[8192 * 2];
            Resampler.Sink sink = new Resampler.Sink() {
                @Override public void write(float[] data, int n) {
                    try {
                        int bi = 0;
                        for (int i = 0; i < n; i++) {
                            float v = data[i];
                            if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
                            int s = (int) (v * 32767f);
                            pcmBytes[bi++] = (byte) (s & 0xff);
                            pcmBytes[bi++] = (byte) ((s >> 8) & 0xff);
                            if (bi == pcmBytes.length) { out.write(pcmBytes, 0, bi); bi = 0; }
                        }
                        if (bi > 0) out.write(pcmBytes, 0, bi);
                        written[0] += n;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            Resampler rs = null;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            float[] mono = new float[0];
            long lastReport = 0;
            while (!outputDone) {
                if (cancel != null && cancel.cancelled()) throw new InterruptedException("abgebrochen");
                if (!inputDone) {
                    int inIdx = codec.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(inIdx);
                        int n = ib == null ? -1 : ex.readSampleData(ib, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long t = ex.getSampleTime();
                            codec.queueInputBuffer(inIdx, 0, n, t < 0 ? 0 : t, 0);
                            ex.advance();
                            if (progress != null && durationUs > 0 && System.currentTimeMillis() - lastReport > 300) {
                                lastReport = System.currentTimeMillis();
                                progress.on(Math.min(1.0, Math.max(0, t) / (double) durationUs));
                            }
                        }
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, 10000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    rs = null;
                } else if (outIdx >= 0) {
                    if (info.size > 0) {
                        ByteBuffer ob = codec.getOutputBuffer(outIdx);
                        if (ob != null) {
                            ob.position(info.offset);
                            ob.limit(info.offset + info.size);
                            ob.order(ByteOrder.LITTLE_ENDIAN);
                            int bytesPer = encoding == 4 ? 4 : 2;
                            int ch = Math.max(1, channels);
                            int frames = info.size / (bytesPer * ch);
                            if (mono.length < frames) mono = new float[frames];
                            for (int f = 0; f < frames; f++) {
                                float acc = 0f;
                                for (int c = 0; c < ch; c++) {
                                    acc += encoding == 4 ? ob.getFloat() : ob.getShort() / 32768f;
                                }
                                mono[f] = acc / ch;
                            }
                            if (rs == null) rs = new Resampler(rate, 16000, sink);
                            rs.process(mono, frames);
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (rs != null) rs.flush();
        } finally {
            try { out.close(); } catch (IOException ignored) {}
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} try { codec.release(); } catch (Exception ignored) {} }
            ex.release();
        }
        if (progress != null) progress.on(1.0);
        return written[0];
    }
}
