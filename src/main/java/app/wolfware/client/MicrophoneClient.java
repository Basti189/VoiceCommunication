package app.wolfware.client;

import app.wolfware.Audio;
import app.wolfware.Device;
import app.wolfware.Settings;
import app.wolfware.TalkState;
import app.wolfware.server.SpeakerClient;

import app.wolfware.AudioInput;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MicrophoneClient implements Runnable, PushToTalkSerial.Listener {

    // Aufnahmepuffer: 40 ms reichen, größer bedeutet nur mehr mögliche Verzögerung
    private static final int INPUT_BUFFER_FRAMES = 4;

    // So oft wird die Zieladresse neu aufgelöst (falls sich die IP hinter einem Hostnamen ändert)
    private static final long RESOLVE_INTERVAL_SECONDS = 30;

    public final Device microphone;

    private volatile boolean stop = false;

    private volatile boolean isMute = true;

    private volatile InetSocketAddress target;

    // Bis zu diesem Zeitpunkt wird der Aufnahmepegel angezeigt (Konsolenbefehl "mic")
    private volatile long meterUntil = 0;

    private PushToTalkSerial pushToTalk;

    private int framePeak = 0;

    private long lastMeterPrint = 0;

    public MicrophoneClient(Device microphone) {
        this.microphone = microphone;
    }

    @Override
    public void run() {
        AudioInput line;
        try {
            line = new Audio().openInputLine(microphone.getIndex(), INPUT_BUFFER_FRAMES);
        } catch (LineUnavailableException e) {
            System.out.println("[MicrophoneClient] unable to open microphone: " + e.getMessage());
            return;
        }

        isMute = Settings.getPushToTalk();
        TalkState.setTransmitting(!isMute);
        if (Settings.getPushToTalk()) {
            pushToTalk = new PushToTalkSerial(this);
            pushToTalk.start();
        }

        // Adresse im Hintergrund auflösen, damit ein langsamer DNS-Server nie die Aufnahme blockiert
        resolveTarget();
        ScheduledExecutorService resolver = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "target-resolver");
            thread.setDaemon(true);
            return thread;
        });
        resolver.scheduleWithFixedDelay(this::resolveTarget, RESOLVE_INTERVAL_SECONDS, RESOLVE_INTERVAL_SECONDS, TimeUnit.SECONDS);

        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] packet = new byte[SpeakerClient.PACKET_BYTES];
            ByteBuffer header = ByteBuffer.wrap(packet);
            DatagramPacket datagram = new DatagramPacket(packet, packet.length);
            int sequence = 0;

            while (!stop) {
                // Immer lesen – auch stummgeschaltet –, damit sich im Mikrofonpuffer keine alten Daten stauen.
                // read() blockiert, bis 10 ms Audio da sind – kein Busy-Waiting.
                int read = line.read(packet, SpeakerClient.HEADER_BYTES, Audio.FRAME_BYTES);
                if (read != Audio.FRAME_BYTES) {
                    continue;
                }
                showLevel(packet);
                if (isMute) {
                    continue;
                }
                InetSocketAddress currentTarget = target;
                if (currentTarget == null || currentTarget.isUnresolved()) {
                    continue;
                }
                header.putInt(0, sequence++);
                datagram.setSocketAddress(currentTarget);
                try {
                    socket.send(datagram);
                } catch (IOException ignore) {
                    // Netzwerk kurz weg – beim nächsten Paket erneut versuchen
                }
            }
        } catch (SocketException e) {
            System.out.println("[MicrophoneClient] socket error: " + e.getMessage());
        } finally {
            resolver.shutdownNow();
            TalkState.setTransmitting(false);
            line.close();
            if (pushToTalk != null) {
                pushToTalk.stop();
            }
            System.out.println("[MicrophoneClient] shutdown");
        }
    }

    /**
     * Startet die Pegelanzeige für ein paar Sekunden. Gemessen wird genau das Signal,
     * das auch gesendet wird – ohne dafür eine zweite Leitung zu öffnen.
     */
    public void showLevelFor(long millis) {
        meterUntil = System.currentTimeMillis() + millis;
        System.out.println("[MicrophoneClient] Pegel des Mikrofons (0-100 %):");
    }

    private void showLevel(byte[] packet) {
        long now = System.currentTimeMillis();
        if (now > meterUntil) {
            return;
        }
        int peak = 0;
        for (int i = SpeakerClient.HEADER_BYTES; i + 1 < packet.length; i += 2) {
            peak = Math.max(peak, Math.abs((short) ((packet[i] & 0xFF) | (packet[i + 1] << 8))));
        }
        framePeak = Math.max(framePeak, peak);
        if (now - lastMeterPrint < 200) {
            return;
        }
        lastMeterPrint = now;
        int percent = framePeak * 100 / 32768;
        int width = percent * 40 / 100;
        String warning = percent > 97 ? "  UEBERSTEUERT" : percent < 2 ? "  (kein Signal)" : "";
        System.out.println("[" + "#".repeat(width) + " ".repeat(40 - width) + "] " + percent + " %"
                + (isMute ? "  (stumm, wird nicht gesendet)" : "") + warning);
        framePeak = 0;
    }

    private void resolveTarget() {
        InetSocketAddress resolved = new InetSocketAddress(Settings.getIp(), Settings.getPort());
        if (resolved.isUnresolved()) {
            System.out.println("[MicrophoneClient] unable to resolve " + Settings.getIp() + ", keeping previous address");
            return;
        }
        if (!resolved.equals(target)) {
            System.out.println("[MicrophoneClient] sending to " + resolved + " (UDP)");
        }
        target = resolved;
    }

    public void stop() {
        stop = true;
    }

    public void setMute(boolean isMute) {
        if (this.isMute == isMute) {
            return;
        }
        if (isMute) {
            System.out.println("[MicrophoneClient] microphone muted");
        } else {
            System.out.println("[MicrophoneClient] you can speak now");
        }
        this.isMute = isMute;
        TalkState.setTransmitting(!isMute);
    }

    public void toggleMute() {
        setMute(!isMute);
    }

    @Override
    public void onPushToTalk(boolean pressed) {
        setMute(!pressed);
    }

    @Override
    public void onPushToTalkLost(String reason) {
        // Sicherer Zustand: Mikrofon aus, unabhängig von invertPushToTalk
        if (!isMute) {
            System.out.println("[MicrophoneClient] " + reason);
        }
        setMute(true);
    }
}
