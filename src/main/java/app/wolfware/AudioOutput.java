package app.wolfware;

import javax.sound.sampled.SourceDataLine;

/**
 * Wiedergabe-Leitung, die nach außen immer mit Mono-Daten arbeitet.
 * Kann das Gerät nur Stereo, wird jeder Abtastwert auf beide Kanäle verdoppelt.
 */
public class AudioOutput {

    private final SourceDataLine line;

    private final boolean stereo;

    private final byte[] scratch;

    AudioOutput(SourceDataLine line, boolean stereo) {
        this.line = line;
        this.stereo = stereo;
        // Gerade Anzahl Stereo-Rahmen (4 Byte), damit beim Stückeln nichts zerrissen wird
        this.scratch = stereo ? new byte[Math.max(4, line.getBufferSize() / 4 * 4)] : new byte[0];
    }

    public boolean isStereo() {
        return stereo;
    }

    /**
     * Schreibt Mono-Daten; die Längenangaben sind immer in Mono-Bytes.
     */
    public void write(byte[] data, int offset, int length) {
        if (!stereo) {
            line.write(data, offset, length);
            return;
        }
        // In Stücken umwandeln, damit auch lange Puffer (Testton) durchpassen
        int chunkBytes = scratch.length / 2;
        for (int done = 0; done < length; done += chunkBytes) {
            int monoBytes = Math.min(chunkBytes, length - done);
            for (int i = 0; i + 1 < monoBytes; i += 2) {
                byte low = data[offset + done + i];
                byte high = data[offset + done + i + 1];
                scratch[i * 2] = low;
                scratch[i * 2 + 1] = high;
                scratch[i * 2 + 2] = low;
                scratch[i * 2 + 3] = high;
            }
            line.write(scratch, 0, monoBytes * 2);
        }
    }

    /**
     * Freier Platz im Puffer, gerechnet in Mono-Bytes.
     */
    public int available() {
        return stereo ? line.available() / 2 : line.available();
    }

    /**
     * Puffergröße, gerechnet in Mono-Bytes.
     */
    public int getBufferSize() {
        return stereo ? line.getBufferSize() / 2 : line.getBufferSize();
    }

    public boolean setVolume(int level) {
        return Audio.setVolume(line, level);
    }

    public void drain() {
        line.drain();
    }

    public void close() {
        line.stop();
        line.flush();
        line.close();
    }
}
