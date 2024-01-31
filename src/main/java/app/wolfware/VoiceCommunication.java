package app.wolfware;

import app.wolfware.client.MicrophoneClient;
import app.wolfware.server.SpeakerServer;

import java.util.List;
import java.util.Scanner;

public class VoiceCommunication {

    public final static Boolean DEBUG = false;

    private final String VERSION = "1.0.0";

    private Device speaker;

    private Device microphone;

    public static void main(String[] args) {
        new VoiceCommunication();
    }

    public VoiceCommunication() {
        System.out.println("VoiceCommunication Ver.: " + VERSION + " by Sebastian Wolf");
        Settings.init();

        setupAudio();

        MicrophoneClient client = null;

        SpeakerServer server = null;

        if (microphone.getIndex() != -1) {
            client = new MicrophoneClient(microphone);
            Thread clientThread = new Thread(client);
            clientThread.start();
        }

        if (speaker.getIndex() != -1) {
            server = new SpeakerServer(speaker);
            Thread serverThread = new Thread(server);
            serverThread.start();
        }

        while(true) {
            Scanner scanner = new Scanner(System.in);
            String in = scanner.next();
            int loudness = -1;
            try {
                loudness = Integer.parseInt(in);
            } catch (NumberFormatException ignore) {

            }
            if (loudness != -1) {
                server.setVolume(loudness);
            } else if (in.equals("stop")) {
                if (server != null) {
                    server.stop();
                }
                if (client != null) {
                    client.stop();
                }
                break;
            } else {
                client.toggleMute();
            }
        }
    }

    private void setupAudio() {
        Audio audio = new Audio();
        while (speaker == null) {
            speaker = setupAudioDevice(Settings.getSpeaker(), audio.getOutputDevice());
        }
        if (!Settings.getSpeaker().equals(speaker.getName())) {
            Settings.setSpeaker(speaker.getName());
        }

        while (microphone == null) {
            microphone = setupAudioDevice(Settings.getMicrophone(), audio.getInputDevice());
        }
        if (!Settings.getMicrophone().equals(microphone.getName())) {
            Settings.setMicrophone(microphone.getName());
        }
    }

    private Device setupAudioDevice(String deviceName, List<Device> devices) {
        // Search Audiodevice from Settings
        devices.add(0, new Device("Aus", -1));
        for (Device device : devices) {
            if (device.getName().equals(deviceName)) {
                return device;
            }
        }

        for (Device device : devices) {
            System.out.println("[" + device.getIndex() + "] " + device.getName());
        }
        Scanner scanner = new Scanner(System.in);
        System.out.print("\n> ");
        String input = scanner.next();

        for (Device device : devices) {
            if (device.getIndex() == Integer.parseInt(input)) {
                return device;
            }
        }
        return null;
    }
}