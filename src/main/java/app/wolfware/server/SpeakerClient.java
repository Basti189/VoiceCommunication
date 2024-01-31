package app.wolfware.server;

import app.wolfware.Audio;
import app.wolfware.Device;
import app.wolfware.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpeakerClient implements Runnable {

    private final Socket client;

    private final Device speaker;

    private volatile boolean stop = false;

    private Audio audio = new Audio();

    public SpeakerClient (Socket client, Device speaker) {
        this.client = client;
        this.speaker = speaker;
    }

    @Override
    public void run() {
        System.out.println("[SpeakerClient] is running...");
        try {
            Thread.sleep(1000);
            audio.getOutputLine(speaker.getIndex());
            audio.startOutputLine();
            System.out.println("[SpeakerClient] set volume to " + Settings.getLoudness());
            audio.setVolume(Settings.getLoudness());
            InputStream in = client.getInputStream();
            while (!stop) {
                byte[] buffer = new byte[1024];
                try {
                    in.read(buffer, 0, buffer.length);
                } catch (IOException ignore) {
                    //Logger.getLogger(SpeakerClient.class.getName()).log(Level.SEVERE, null, ex);
                };
                audio.getSourceLine().write(buffer, 0, buffer.length);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[SpeakerClient] stopped...");
    }

    public void stop() {
        stop = true;
        try {
            client.close();
        } catch (IOException ignore) {
        }
    }

    public void setVolume(int level) {
        System.out.println("[SpeakerClient] set Volume to " + level);
        audio.setVolume(level);
    }
}
