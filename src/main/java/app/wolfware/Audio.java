package app.wolfware;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class Audio {

    // 48 kHz, 16 Bit, Mono – für Sprache völlig ausreichend, ein Viertel der Daten von 44,1 kHz Stereo
    public static final AudioFormat FORMAT = new AudioFormat(48000f, 16, 1, true, false);

    // Ersatzformat: Viele Soundkarten (analoge Ausgänge, Line-In) können ausschließlich Stereo.
    // Dann wird das Mono-Signal beim Schreiben verdoppelt bzw. beim Lesen zusammengemischt.
    public static final AudioFormat STEREO_FORMAT = new AudioFormat(48000f, 16, 2, true, false);

    // 10 ms pro Paket: 480 Samples * 2 Byte = 960 Byte (passt ohne Fragmentierung in ein UDP-Paket)
    public static final int FRAME_BYTES = 960;

    // Zum Auflisten NUR die Art der Leitung prüfen, nicht das Format – sonst fehlen Geräte,
    // die kein Mono können (z. B. der analoge Ausgang des Mainboards)
    private static final Line.Info ANY_INPUT = new Line.Info(TargetDataLine.class);

    private static final Line.Info ANY_OUTPUT = new Line.Info(SourceDataLine.class);

    private final Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();

    // Puffergröße explizit klein halten – der Standardpuffer von Java Sound ist oft 0,5–1 s groß
    public AudioInput openInputLine(int index, int bufferFrames) throws LineUnavailableException {
        for (AudioFormat format : new AudioFormat[]{FORMAT, STEREO_FORMAT}) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) findLine(index, info, ANY_INPUT);
            if (line == null) {
                continue;
            }
            line.open(format, bufferFrames * FRAME_BYTES * format.getChannels());
            line.start();
            if (format.getChannels() == 2) {
                System.out.println("[Audio] Aufnahmegeraet kann nur Stereo, beide Kanaele werden gemischt");
            }
            return new AudioInput(line, format.getChannels() == 2);
        }
        throw new LineUnavailableException("Gerät unterstützt weder 48 kHz Mono noch Stereo");
    }

    public AudioOutput openOutputLine(int index, int bufferFrames) throws LineUnavailableException {
        for (AudioFormat format : new AudioFormat[]{FORMAT, STEREO_FORMAT}) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) findLine(index, info, ANY_OUTPUT);
            if (line == null) {
                continue;
            }
            line.open(format, bufferFrames * FRAME_BYTES * format.getChannels());
            line.start();
            if (format.getChannels() == 2) {
                System.out.println("[Audio] Wiedergabegeraet kann nur Stereo, Mono wird auf beide Kanaele gelegt");
            }
            return new AudioOutput(line, format.getChannels() == 2);
        }
        throw new LineUnavailableException("Gerät unterstützt weder 48 kHz Mono noch Stereo");
    }

    /**
     * Holt die Leitung vom gewünschten Gerät; ist sie belegt oder das Gerät verschwunden,
     * wird auf den Systemstandard ausgewichen.
     *
     * @return null, wenn das Gerät dieses Format nicht kann (dann wird das nächste Format probiert)
     */
    private Line findLine(int index, DataLine.Info info, Line.Info anyInfo) throws LineUnavailableException {
        if (index != Device.SYSTEM_DEFAULT) {
            try {
                return AudioSystem.getMixer(mixerInfo[index]).getLine(info);
            } catch (IllegalArgumentException e) {
                // Format wird nicht unterstützt – der Aufrufer probiert das nächste
                return null;
            } catch (LineUnavailableException e) {
                // Gerät belegt (unter Linux hält oft PipeWire die Karte) – lieber Systemstandard als gar nichts
                System.out.println("[Audio] device busy (" + e.getMessage() + "), falling back to system default");
            }
        }
        int defaultIndex = findSystemDefault(anyInfo);
        try {
            if (defaultIndex >= 0) {
                return AudioSystem.getMixer(mixerInfo[defaultIndex]).getLine(info);
            }
            // Letzte Möglichkeit: Java Sound selbst entscheiden lassen
            return AudioSystem.getLine(info);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<Device> getInputDevice() {
        return findDevices(ANY_INPUT, new DataLine.Info(TargetDataLine.class, FORMAT));
    }

    public List<Device> getOutputDevice() {
        return findDevices(ANY_OUTPUT, new DataLine.Info(SourceDataLine.class, FORMAT));
    }

    private List<Device> findDevices(Line.Info anyInfo, DataLine.Info monoInfo) {
        List<Device> list = new ArrayList<>();
        for (int i = 0; i < mixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo[i]);
            if (!mixer.isLineSupported(anyInfo)) {
                continue;
            }
            String description = describe(mixerInfo[i]);
            if (!mixer.isLineSupported(monoInfo)) {
                description = description.isEmpty() ? "nur Stereo" : description + ", nur Stereo";
            }
            list.add(new Device(mixerInfo[i].getName(), i, description));
        }
        return list;
    }

    /**
     * Soundkarten, die das System kennt und die den gewünschten Anschluss haben, für die Java aber
     * keine Leitung anbietet. Unter Linux ist der Grund fast immer, dass ein anderes Programm
     * (PipeWire/PulseAudio) die Karte exklusiv geöffnet hat – ALSA meldet sie dann mit 0 freien
     * Sub-Geräten und Java nimmt sie nicht in die Liste auf.
     *
     * @return Beschreibungen der betroffenen Karten, für einen Hinweis bei der Auswahl
     */
    public List<String> getBlockedDevices(boolean input) {
        Line.Info wanted = input ? ANY_INPUT : ANY_OUTPUT;
        List<String> usable = new ArrayList<>();
        for (Mixer.Info info : mixerInfo) {
            if (AudioSystem.getMixer(info).isLineSupported(wanted)) {
                usable.add(cardName(info.getName()));
            }
        }

        List<String> blocked = new ArrayList<>();
        for (Mixer.Info info : mixerInfo) {
            // Zu jeder Karte gibt es einen Port-Mixer, auch wenn die Karte gerade belegt ist
            if (!info.getName().startsWith("Port ")) {
                continue;
            }
            String card = cardName(info.getName().substring("Port ".length()));
            if (usable.contains(card) || blocked.contains(card)) {
                continue;
            }
            // Nur melden, wenn die Karte den gesuchten Anschluss überhaupt hat –
            // eine Webcam ohne Lautsprecher ist nicht "belegt", sondern schlicht kein Ausgang
            if (!hasPort(AudioSystem.getMixer(info), input)) {
                continue;
            }
            String description = info.getDescription();
            blocked.add(card + (description == null || description.isEmpty() ? "" : " (" + description + ")"));
        }
        return blocked;
    }

    /**
     * Prüft, ob die Karte einen Eingang (Mikrofon, Line-In) bzw. Ausgang (Lautsprecher,
     * Kopfhörer) besitzt. Bei Java Sound sind Aufnahme-Anschlüsse Quellen des Mixers
     * (isSource true), Wiedergabe-Anschlüsse dessen Ziele.
     */
    private boolean hasPort(Mixer mixer, boolean input) {
        Line.Info[] infos = input ? mixer.getSourceLineInfo() : mixer.getTargetLineInfo();
        for (Line.Info info : infos) {
            if (info instanceof Port.Info portInfo && portInfo.isSource() == input) {
                return true;
            }
        }
        return false;
    }

    // "Generic [plughw:2,0]" und "Port Generic [hw:2]" gehören zur selben Karte "Generic"
    private String cardName(String mixerName) {
        int bracket = mixerName.indexOf(" [");
        return bracket < 0 ? mixerName : mixerName.substring(0, bracket);
    }

    // Unter Linux sind die reinen Gerätenamen kaum zu unterscheiden – Beschreibung hilft bei der Auswahl
    private String describe(Mixer.Info info) {
        String description = info.getDescription();
        if (description == null) {
            return "";
        }
        description = description.trim();
        // Bei ALSA steht hier oft nur "Direct Audio Device: ..." – das bringt nichts
        if (description.equalsIgnoreCase(info.getName())) {
            return "";
        }
        return description;
    }

    /**
     * Sucht das Gerät, das für "Systemstandard" steht: unter Windows den primären Soundtreiber,
     * unter Linux das ALSA-Gerät "default" (das bei PipeWire/PulseAudio im Lautstärkemixer
     * frei zugeordnet werden kann).
     *
     * @return Index in mixerInfo oder -1
     */
    private int findSystemDefault(Line.Info info) {
        for (int i = 0; i < mixerInfo.length; i++) {
            String name = mixerInfo[i].getName().toLowerCase();
            boolean isDefault = name.contains("primary sound") || name.contains("primärer sound")
                    || name.contains("[default]") || name.contains("pipewire") || name.contains("pulseaudio");
            if (isDefault && AudioSystem.getMixer(mixerInfo[i]).isLineSupported(info)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Name des Geräts, das "Systemstandard" gerade bedeutet – nur zur Anzeige bei der Auswahl.
     */
    public String getSystemDefaultName(boolean input) {
        int index = findSystemDefault(input ? ANY_INPUT : ANY_OUTPUT);
        return index < 0 ? "" : mixerInfo[index].getName();
    }

    /**
     * Stellt die Lautstärke über die Soundkarte ein.
     *
     * @return false, wenn die Leitung keine Lautstärkeregelung hat (unter Linux die Regel) –
     * dann muss die Lautstärke in der Software gerechnet werden
     */
    public static boolean setVolume(SourceDataLine line, int level) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return false;
        }
        FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        level = Math.max(0, Math.min(100, level));
        // Lautstärke logarithmisch in dB umrechnen: 100 % = 0 dB, 50 % = -6 dB, 0 % = stumm
        float db = level == 0 ? control.getMinimum() : (float) (20.0 * Math.log10(level / 100.0));
        control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), db)));
        return true;
    }

    /**
     * Gleiche Kennlinie wie setVolume, aber als Faktor zum Multiplizieren der Abtastwerte.
     * 10^(20*log10(level/100)/20) kürzt sich zu level/100.
     */
    public static float toGain(int level) {
        return Math.max(0, Math.min(100, level)) / 100f;
    }

    /**
     * Wendet den Faktor auf 16-Bit-Little-Endian-Abtastwerte an (mit Begrenzung gegen Übersteuern).
     */
    public static void applyGain(byte[] data, int offset, int length, float gain) {
        for (int i = offset; i + 1 < offset + length; i += 2) {
            int sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
            sample = Math.round(sample * gain);
            sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            data[i] = (byte) sample;
            data[i + 1] = (byte) (sample >> 8);
        }
    }
}
