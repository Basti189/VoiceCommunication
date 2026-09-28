package app.wolfware.server;

import app.wolfware.Audio;
import app.wolfware.Device;
import app.wolfware.Settings;
import app.wolfware.TalkState;

import app.wolfware.AudioOutput;

import javax.sound.sampled.LineUnavailableException;
import java.nio.ByteBuffer;

/**
 * Wiedergabe eines einzelnen Absenders mit kleinem, selbstregelndem Puffer.
 * Paketaufbau: 4 Byte Sequenznummer + Audio.FRAME_BYTES Audiodaten.
 */
public class SpeakerClient {

    public static final int HEADER_BYTES = 4;

    public static final int PACKET_BYTES = HEADER_BYTES + Audio.FRAME_BYTES;

    // Nach einer Pause mit 30 ms Vorlauf starten (Puffer gegen Netzwerk-Jitter)
    private static final int TARGET_FRAMES = 3;

    // Mehr als 60 ms im Puffer → Pakete verwerfen, statt Verzögerung anzuhäufen
    private static final int MAX_FRAMES = 6;

    private static final int LINE_BUFFER_FRAMES = 12;

    // Springt die Sequenznummer so weit zurück, wurde der Absender neu gestartet
    private static final int RESTART_THRESHOLD = 500;

    private final AudioOutput line;

    private final int maxQueuedBytes;

    // Eigener Puffer, weil der Empfangspuffer des Servers wiederverwendet wird
    private final byte[] scratch = new byte[Audio.FRAME_BYTES];

    // Lautstärke in der Software rechnen, wenn die Soundkarte keine Regelung anbietet (unter Linux die Regel)
    private boolean softwareVolume = false;

    private volatile float gain = 1f;

    private final byte[] silence = new byte[(TARGET_FRAMES - 1) * Audio.FRAME_BYTES];

    private boolean hasSequence = false;

    private int lastSequence;

    private volatile long lastPacketAt = System.currentTimeMillis();

    public SpeakerClient(Audio audio, Device speaker) throws LineUnavailableException {
        line = audio.openOutputLine(speaker.getIndex(), LINE_BUFFER_FRAMES);
        // Der Treiber darf die Puffergröße anpassen – deshalb mit der tatsächlichen Größe rechnen
        maxQueuedBytes = Math.min(MAX_FRAMES * Audio.FRAME_BYTES, line.getBufferSize() - Audio.FRAME_BYTES);
    }

    public void play(byte[] packet) {
        lastPacketAt = System.currentTimeMillis();

        // Doppelte oder verspätete Pakete verwerfen (Vergleich ist überlaufsicher).
        // Großer Rücksprung = Absender neu gestartet → Zählung neu beginnen.
        int sequence = ByteBuffer.wrap(packet, 0, HEADER_BYTES).getInt();
        int delta = sequence - lastSequence;
        if (hasSequence && delta <= 0 && delta > -RESTART_THRESHOLD) {
            return;
        }
        hasSequence = true;
        lastSequence = sequence;

        // Halbduplex: Solange hier selbst gesprochen wird, die Gegenseite nicht abspielen,
        // damit sie nicht über das eigene Mikrofon zurückgeschickt wird
        if (Settings.getHalfDuplex() && TalkState.isTransmitting()) {
            return;
        }

        int queued = line.getBufferSize() - line.available();

        // Puffer zu voll (Taktabweichung der Soundkarten, Paket-Burst) → verwerfen, Latenz bleibt konstant
        if (queued > maxQueuedBytes) {
            return;
        }

        // Puffer leergelaufen (Sprechpause, Paketverlust) → kurzen Vorlauf aus Stille einfügen
        if (queued == 0) {
            line.write(silence, 0, silence.length);
        }

        // Blockiert nicht, da oben sichergestellt ist, dass genug Platz frei ist
        if (softwareVolume) {
            System.arraycopy(packet, HEADER_BYTES, scratch, 0, Audio.FRAME_BYTES);
            Audio.applyGain(scratch, 0, Audio.FRAME_BYTES, gain);
            line.write(scratch, 0, Audio.FRAME_BYTES);
        } else {
            line.write(packet, HEADER_BYTES, Audio.FRAME_BYTES);
        }
    }

    public long getLastPacketAt() {
        return lastPacketAt;
    }

    public void setVolume(int level) {
        if (line.setVolume(level)) {
            softwareVolume = false;
            return;
        }
        softwareVolume = true;
        gain = Audio.toGain(level);
    }

    public void close() {
        line.close();
    }
}
