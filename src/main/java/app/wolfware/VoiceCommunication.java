package app.wolfware;

import app.wolfware.client.MicrophoneClient;
import app.wolfware.server.SpeakerServer;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class VoiceCommunication {

    public final static Boolean DEBUG = false;

    private final String VERSION = "2.9.0";

    // Ein einziger Scanner für die ganze Laufzeit
    private final Scanner scanner = new Scanner(System.in);

    private final AudioCheck audioCheck = new AudioCheck();

    private Device speaker;

    private Device microphone;

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("devices") || args[0].equals("-l"))) {
            AudioDiagnostics.print();
            return;
        }
        new VoiceCommunication();
    }

    public VoiceCommunication() {
        System.out.println("VoiceCommunication Ver.: " + VERSION + " by Sebastian Wolf");
        Settings.init();

        setupAudio();

        if (Settings.getHalfDuplex() && !Settings.getPushToTalk()) {
            System.out.println("WARNING: halfDuplex without pushToTalk keeps the speaker muted while the microphone is open");
        }

        MicrophoneClient client = null;

        SpeakerServer server = null;

        if (microphone.getIndex() != Device.DISABLED) {
            client = new MicrophoneClient(microphone);
            Thread clientThread = new Thread(client);
            clientThread.start();
        }

        if (speaker.getIndex() != Device.DISABLED) {
            server = new SpeakerServer(speaker);
            Thread serverThread = new Thread(server);
            serverThread.start();
        }

        try {
            while (true) {
                String in = scanner.next();
                if (in.equals("stop")) {
                    if (server != null) {
                        server.stop();
                    }
                    if (client != null) {
                        client.stop();
                    }
                    break;
                }
                if (in.equals("mic")) {
                    if (client == null) {
                        System.out.println("Kein Mikrofon eingerichtet");
                    } else {
                        client.showLevelFor(8000);
                    }
                    continue;
                }
                if (in.equals("devices")) {
                    AudioDiagnostics.print();
                    continue;
                }
                if (in.equals("test")) {
                    // Testton auf dem eingestellten Lautsprecher
                    audioCheck.playTestTone(speaker);
                    continue;
                }
                try {
                    int loudness = Integer.parseInt(in);
                    if (server != null) {
                        server.setVolume(loudness);
                    }
                } catch (NumberFormatException e) {
                    if (client != null) {
                        client.toggleMute();
                    }
                }
            }
        } catch (NoSuchElementException e) {
            // Keine Konsole vorhanden (z. B. Autostart ohne Fenster) – Audio-Threads laufen weiter
            System.out.println("No console input available, running without console commands");
        }
    }

    private void setupAudio() {
        Audio audio = new Audio();
        while (speaker == null) {
            speaker = setupAudioDevice("speaker", Settings.getSpeaker(), audio.getOutputDevice());
        }
        while (microphone == null) {
            microphone = setupAudioDevice("microphone", Settings.getMicrophone(), audio.getInputDevice());
        }
        // Nur echte Auswahl speichern – ein Ersatzgerät überschreibt die Konfiguration nicht,
        // damit das eigentliche Gerät beim nächsten Start wieder gefunden wird
        if (!Settings.getSpeaker().equals(speaker.getName()) && !speaker.isFallback()) {
            Settings.setSpeaker(speaker.getName());
        }
        if (!Settings.getMicrophone().equals(microphone.getName()) && !microphone.isFallback()) {
            Settings.setMicrophone(microphone.getName());
        }
    }

    private Device setupAudioDevice(String role, String deviceName, List<Device> available) {
        boolean isSpeaker = role.equals("speaker");

        Audio audio = new Audio();
        List<Device> devices = new ArrayList<>();
        devices.add(Device.systemDefault(audio.getSystemDefaultName(!isSpeaker)));
        devices.add(new Device("Aus", Device.DISABLED, "Gerät nicht verwenden"));
        devices.addAll(available);

        // Gerät aus der Konfiguration suchen
        for (Device device : devices) {
            if (device.getName().equals(deviceName)) {
                return device;
            }
        }

        // Gerät ist konfiguriert, aber nicht vorhanden (z. B. USB-Headset umgesteckt):
        // nicht auf eine Eingabe warten, sondern den Systemstandard nehmen
        if (deviceName != null && !deviceName.isEmpty()) {
            System.out.println("WARNING: " + role + " '" + deviceName + "' not found, using system default instead");
            return Device.systemDefault().asFallback();
        }

        // Erster Start ohne Konfiguration: Gerät über die Konsole auswählen
        System.out.println();
        for (Device device : devices) {
            String description = device.getDescription().isEmpty() ? "" : " - " + device.getDescription();
            System.out.println("[" + device.getIndex() + "] " + device.getName() + description);
        }
        List<String> blocked = audio.getBlockedDevices(!isSpeaker);
        if (!blocked.isEmpty()) {
            System.out.println("\nNicht nutzbar, weil gerade von einem anderen Programm belegt");
            System.out.println("(unter Linux meist PipeWire/PulseAudio):");
            for (String card : blocked) {
                System.out.println("    " + card);
            }
        }
        System.out.println("\nTipp: '-2' nutzt das Standardgerät des Systems. Die Zuordnung erfolgt dann");
        System.out.println("in den Sound-Einstellungen (Windows) bzw. im Lautstärkemixer (Linux).");
        System.out.print("\n" + role + " > ");
        String input = scanner.next();

        Device selected = null;
        try {
            int index = Integer.parseInt(input);
            for (Device device : devices) {
                if (device.getIndex() == index) {
                    selected = device;
                    break;
                }
            }
        } catch (NumberFormatException ignore) {
            // Ungültige Eingabe → Auswahl wird erneut angezeigt
        }
        if (selected == null || selected.getIndex() == Device.DISABLED) {
            return selected;
        }

        // Ausprobieren, denn unter Linux sagen die Gerätenamen kaum etwas aus
        boolean ok = isSpeaker ? audioCheck.playTestTone(selected) : audioCheck.showInputLevel(selected);
        if (!ok) {
            System.out.println("Bitte ein anderes Gerät wählen.");
            return null;
        }
        System.out.print(isSpeaker ? "Ton gehört? [j/n] > " : "Pegel ausgeschlagen? [j/n] > ");
        String answer = scanner.next();
        if (answer.equalsIgnoreCase("j") || answer.equalsIgnoreCase("y")) {
            return selected;
        }
        return null;
    }
}
