package app.wolfware.server;

import app.wolfware.Audio;
import app.wolfware.Device;
import app.wolfware.Settings;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpeakerServer implements Runnable {

    // Wiedergabe bleibt lange offen, damit beim nächsten Sprechen nichts abgeschnitten wird.
    // Geschlossen wird nur, wenn ein Absender eine Stunde lang gar nichts sendet.
    private static final long STREAM_TIMEOUT_MS = 60L * 60L * 1000L;

    private final Device speaker;

    private final Audio audio = new Audio();

    // Ein Wiedergabestrom pro Absender-Rechner (nur IP, damit ein Neustart des Absenders
    // mit neuem Quellport dieselbe Leitung weiterverwendet)
    private final Map<InetAddress, SpeakerClient> streams = new ConcurrentHashMap<>();

    private volatile boolean stop = false;

    private volatile int volume = Settings.getLoudness();

    public SpeakerServer(Device speaker) {
        this.speaker = speaker;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(Settings.getPort())) {
            socket.setSoTimeout(1000);
            System.out.println("[SpeakerServer] listening on UDP port " + Settings.getPort());

            byte[] buffer = new byte[SpeakerClient.PACKET_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            long lastCleanup = System.currentTimeMillis();

            while (!stop) {
                try {
                    packet.setLength(buffer.length);
                    socket.receive(packet);
                    if (packet.getLength() == SpeakerClient.PACKET_BYTES) {
                        SpeakerClient stream = streams.computeIfAbsent(packet.getAddress(), this::openStream);
                        if (stream != null) {
                            stream.play(buffer);
                        }
                    }
                } catch (SocketTimeoutException ignore) {
                    // Kein Paket – nur Aufräumen prüfen
                }

                long now = System.currentTimeMillis();
                if (now - lastCleanup > 1000) {
                    closeIdleStreams(now);
                    lastCleanup = now;
                }
            }
        } catch (IOException e) {
            System.out.println("[SpeakerServer] error: " + e.getMessage());
        } finally {
            streams.values().forEach(SpeakerClient::close);
            streams.clear();
            System.out.println("[SpeakerServer] shutdown...");
        }
    }

    private SpeakerClient openStream(InetAddress sender) {
        try {
            SpeakerClient stream = new SpeakerClient(audio, speaker);
            stream.setVolume(volume);
            System.out.println("[SpeakerServer] new stream from " + sender);
            return stream;
        } catch (LineUnavailableException e) {
            System.out.println("[SpeakerServer] unable to open speaker: " + e.getMessage());
            return null;
        }
    }

    private void closeIdleStreams(long now) {
        streams.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getLastPacketAt() > STREAM_TIMEOUT_MS) {
                System.out.println("[SpeakerServer] stream from " + entry.getKey() + " closed (timeout)");
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    public void stop() {
        stop = true;
    }

    public void setVolume(int level) {
        volume = level;
        Settings.setLoudness(level);
        for (SpeakerClient stream : streams.values()) {
            stream.setVolume(level);
        }
    }
}
