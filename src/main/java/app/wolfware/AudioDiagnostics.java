package app.wolfware;

import javax.sound.sampled.*;

/**
 * Listet alles auf, was Java Sound auf diesem Rechner sieht – inklusive der Geräte, die für
 * VoiceCommunication nicht in Frage kommen. Hilft, wenn ein Gerät in der Auswahl fehlt.
 * Aufruf: java -jar VoiceCommunication.jar devices
 */
public class AudioDiagnostics {

    public static void print() {
        System.out.println("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS  : " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println();

        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        System.out.println(mixerInfo.length + " Mixer gefunden:");
        System.out.println();

        for (int i = 0; i < mixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo[i]);
            System.out.println("[" + i + "] " + mixerInfo[i].getName());
            System.out.println("     Beschreibung: " + mixerInfo[i].getDescription());
            System.out.println("     Hersteller  : " + mixerInfo[i].getVendor() + " / " + mixerInfo[i].getVersion());
            System.out.println("     Klasse      : " + mixer.getClass().getSimpleName());
            printLines(mixer, "Wiedergabe", mixer.getSourceLineInfo());
            printLines(mixer, "Aufnahme  ", mixer.getTargetLineInfo());
            System.out.println();
        }

        System.out.println("Gesucht wird: " + Audio.FORMAT);
        System.out.println("Ersatzweise : " + Audio.STEREO_FORMAT);
        System.out.println();
        System.out.println("Fehlt hier ein Geraet, kennt Java es nicht. Zum Vergleich unter Linux:");
        System.out.println("  aplay -l                  (Wiedergabe-Karten, die ALSA kennt)");
        System.out.println("  arecord -l                (Aufnahme-Karten)");
        System.out.println("  pactl list short sinks    (Geraete, die PipeWire/PulseAudio kennt)");
    }

    private static void printLines(Mixer mixer, String role, Line.Info[] infos) {
        for (Line.Info info : infos) {
            if (!(info instanceof DataLine.Info dataInfo)) {
                continue;
            }
            // Nur die für uns relevanten Leitungsarten
            if (dataInfo.getLineClass() != SourceDataLine.class && dataInfo.getLineClass() != TargetDataLine.class) {
                continue;
            }
            System.out.println("     " + role + "  : " + dataInfo.getLineClass().getSimpleName()
                    + ", Puffer " + dataInfo.getMinBufferSize() + "-" + dataInfo.getMaxBufferSize());
            for (AudioFormat format : dataInfo.getFormats()) {
                // Nur 16-Bit-Formate, sonst wird die Liste unuebersichtlich
                if (format.getSampleSizeInBits() == 16) {
                    System.out.println("        " + describe(format));
                }
            }
        }
        // Passt unser Wunschformat? Das entscheidet, ob das Geraet in der Auswahl auftaucht
        if (role.startsWith("Wiedergabe")) {
            report(mixer, SourceDataLine.class);
        } else {
            report(mixer, TargetDataLine.class);
        }
    }

    private static void report(Mixer mixer, Class<?> lineClass) {
        boolean any = mixer.isLineSupported(new Line.Info(lineClass));
        if (!any) {
            return;
        }
        boolean mono = mixer.isLineSupported(new DataLine.Info(lineClass, Audio.FORMAT));
        boolean stereo = mixer.isLineSupported(new DataLine.Info(lineClass, Audio.STEREO_FORMAT));
        System.out.println("     -> nutzbar: " + (mono ? "Mono" : stereo ? "nur Stereo" : "NEIN (48 kHz nicht moeglich)"));
    }

    private static String describe(AudioFormat format) {
        String rate = format.getSampleRate() == AudioSystem.NOT_SPECIFIED
                ? "beliebige Rate" : (int) format.getSampleRate() + " Hz";
        String channels = format.getChannels() == AudioSystem.NOT_SPECIFIED
                ? "beliebige Kanaele" : format.getChannels() + " Kanal/Kanaele";
        return rate + ", " + channels + ", " + format.getEncoding()
                + (format.isBigEndian() ? ", big endian" : ", little endian");
    }
}
