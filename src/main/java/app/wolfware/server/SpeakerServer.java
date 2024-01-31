package app.wolfware.server;

import app.wolfware.Device;
import app.wolfware.Settings;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SpeakerServer implements Runnable {

    public final Device speaker;

    private volatile boolean stop = false;

    private List<SpeakerClient> clients = new ArrayList<>();

    public SpeakerServer(Device speaker) {
        this.speaker = speaker;
    }

    @Override
    public void run() {
        ServerSocket socket;
        clients = new ArrayList<>();

        try {
            socket = new ServerSocket(Settings.getPort());
            socket.setSoTimeout(2000);
            System.out.println("[SpeakerServer] listening on port " + Settings.getPort());

            while (!stop) {
                try {
                    Socket client = socket.accept();
                    SpeakerClient speakerClient = new SpeakerClient(client, speaker);
                    Thread clientThread = new Thread(speakerClient);
                    clientThread.start();
                    clients.add(speakerClient);
                } catch (IOException ignore) {

                }

            }
            System.out.println("[SpeakerServer] wait for shutdown...");
            for (SpeakerClient speakerClient : clients) {
                speakerClient.stop();
            }
            clients.clear();
            socket.close();
            System.out.println("[SpeakerServer] shutdown...");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        stop = true;
    }

    public void setVolume(int level) {
        for (SpeakerClient speakerClient : clients) {
            speakerClient.setVolume(level);
        }
    }

}
