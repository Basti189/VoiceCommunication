package app.wolfware;

import javax.sound.sampled.LineUnavailableException;

/**
 * Kurzer Test von Lautsprecher und Mikrofon.
 * Unter Linux heißen die Geräte oft nur "Wireless" oder "HDMI [plughw:1,3]" – hören bzw.
 * sehen ist dort der einzige verlässliche Weg, das richtige Gerät zu finden.
 */
public class AudioCheck {

    private static final int TEST_TONE_MS = 1200;

    private static final int LEVEL_METER_MS = 5000;

    private static final float TONE_HZ = 440f;

    private final Audio audio = new Audio();

    /**
     * Spielt einen kurzen Testton ab.
     *
     * @return false, wenn das Gerät nicht geöffnet werden konnte
     */
    public boolean playTestTone(Device device) {
        AudioOutput line = null;
        try {
            line = audio.openOutputLine(device.getIndex(), 20);
            line.setVolume(100);

            int samples = (int) (Audio.FORMAT.getSampleRate() * TEST_TONE_MS / 1000);
            byte[] tone = new byte[samples * 2];
            int fade = (int) (Audio.FORMAT.getSampleRate() * 0.02);
            for (int i = 0; i < samples; i++) {
                double value = Math.sin(2 * Math.PI * TONE_HZ * i / Audio.FORMAT.getSampleRate());
                // Ein- und ausblenden, sonst knackt es am Anfang und Ende
                double envelope = Math.min(1.0, Math.min(i, samples - i) / (double) fade);
                short sample = (short) (value * envelope * 8000);
                tone[i * 2] = (byte) sample;
                tone[i * 2 + 1] = (byte) (sample >> 8);
            }

            System.out.println("Testton wird abgespielt...");
            line.write(tone, 0, tone.length);
            line.drain();
            return true;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.out.println("Gerät konnte nicht geöffnet werden: " + e.getMessage());
            return false;
        } finally {
            if (line != null) {
                line.close();
            }
        }
    }

    /**
     * Zeigt den Eingangspegel des Mikrofons als Balken an.
     *
     * @return false, wenn das Gerät nicht geöffnet werden konnte
     */
    public boolean showInputLevel(Device device) {
        AudioInput line = null;
        try {
            line = audio.openInputLine(device.getIndex(), 8);

            System.out.println("Bitte " + (LEVEL_METER_MS / 1000) + " Sekunden lang sprechen:");
            byte[] buffer = new byte[Audio.FRAME_BYTES];
            long end = System.currentTimeMillis() + LEVEL_METER_MS;
            int peak = 0;
            int overall = 0;
            long lastPrint = 0;

            while (System.currentTimeMillis() < end) {
                int read = line.read(buffer, 0, buffer.length);
                for (int i = 0; i + 1 < read; i += 2) {
                    int sample = Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8)));
                    peak = Math.max(peak, sample);
                }
                long now = System.currentTimeMillis();
                if (now - lastPrint >= 200) {
                    lastPrint = now;
                    overall = Math.max(overall, peak);
                    System.out.println(bar(peak));
                    peak = 0;
                }
            }

            if (overall < 500) {
                System.out.println("Es kam so gut wie kein Signal an - vermutlich das falsche Gerät.");
                return false;
            }
            if (overall > 32000) {
                System.out.println("Hinweis: Das Signal übersteuert. Mikrofonpegel im System etwas verringern.");
            }
            return true;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.out.println("Gerät konnte nicht geöffnet werden: " + e.getMessage());
            return false;
        } finally {
            if (line != null) {
                line.close();
            }
        }
    }

    private String bar(int peak) {
        int width = Math.min(40, peak * 40 / 32768);
        return "[" + "#".repeat(width) + " ".repeat(40 - width) + "]";
    }
}
