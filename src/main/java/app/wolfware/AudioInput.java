package app.wolfware;

import javax.sound.sampled.TargetDataLine;

/**
 * Aufnahme-Leitung, die nach außen immer Mono liefert.
 * Kann das Gerät nur Stereo, werden beide Kanäle zusammengemischt.
 */
public class AudioInput {

    private final TargetDataLine line;

    private final boolean stereo;

    private final byte[] scratch;

    AudioInput(TargetDataLine line, boolean stereo) {
        this.line = line;
        this.stereo = stereo;
        // Gerade Anzahl Stereo-Rahmen (4 Byte), damit beim Stückeln nichts zerrissen wird
        this.scratch = stereo ? new byte[Math.max(4, line.getBufferSize() / 4 * 4)] : new byte[0];
    }

    public boolean isStereo() {
        return stereo;
    }

    /**
     * Liest Mono-Daten; die Längenangaben sind immer in Mono-Bytes.
     *
     * @return gelesene Mono-Bytes
     */
    public int read(byte[] data, int offset, int length) {
        if (!stereo) {
            return line.read(data, offset, length);
        }
        int mono = 0;
        int chunkBytes = scratch.length / 2;
        while (mono < length) {
            int want = Math.min(chunkBytes, length - mono);
            int read = line.read(scratch, 0, want * 2);
            if (read <= 0) {
                break;
            }
            for (int i = 0; i + 3 < read; i += 4) {
                int left = (short) ((scratch[i] & 0xFF) | (scratch[i + 1] << 8));
                int right = (short) ((scratch[i + 2] & 0xFF) | (scratch[i + 3] << 8));
                // Mittelwert statt Summe, damit zwei laute Kanäle nicht übersteuern
                int sample = (left + right) / 2;
                data[offset + mono] = (byte) sample;
                data[offset + mono + 1] = (byte) (sample >> 8);
                mono += 2;
            }
        }
        return mono;
    }

    public void close() {
        line.stop();
        line.close();
    }
}
