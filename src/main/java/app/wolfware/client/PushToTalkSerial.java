package app.wolfware.client;

import app.wolfware.Settings;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sprechtaste über den ESP an der seriellen Schnittstelle.
 * <p>
 * - findet den ESP automatisch (comPort=auto): Kandidaten über USB-Hersteller-ID/Seriennummer filtern,
 *   dann nur den Port übernehmen, der tatsächlich "True"/"False" sendet – so wird kein fremder
 *   USB-Seriell-Adapter (z. B. Simulator-Hardware mit gleichem Wandler-Chip) verwechselt
 * - oder nutzt einen festen Port (comPort=COM3)
 * - verbindet sich nach Abziehen/Einstecken selbstständig neu
 * - schaltet das Mikrofon stumm, wenn die Verbindung abreißt oder der ESP zu lange nichts meldet
 */
public class PushToTalkSerial implements SerialPortDataListener {

    public interface Listener {

        // Sprechtaste gedrückt (true) oder losgelassen (false), bereits um invertPushToTalk bereinigt
        void onPushToTalk(boolean pressed);

        // Verbindung verloren oder Zeitüberschreitung – Mikrofon muss sicher stumm werden
        void onPushToTalkLost(String reason);
    }

    private static final long RECONNECT_INTERVAL_MS = 2000;

    private static final long WATCHDOG_INTERVAL_MS = 50;

    // So lange hat ein automatisch gefundener Port Zeit, sich mit "True"/"False" zu melden.
    // Großzügig, weil der ESP8266 beim Öffnen des Ports neu startet.
    private static final long PROBE_TIMEOUT_MS = 3000;

    private final Listener listener;

    private final StringBuilder receivedData = new StringBuilder();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "push-to-talk-serial");
        thread.setDaemon(true);
        return thread;
    });

    private volatile SerialPort serialPort;

    private volatile long lastMessageAt = 0;

    private volatile boolean pressed = false;

    private long lastConnectAttempt = 0;

    private long connectedAt = 0;

    // true, sobald der Port eine gültige Meldung geschickt hat (bei festem Port sofort)
    private volatile boolean confirmed = false;

    // Reihum-Zähler, damit bei mehreren Kandidaten nicht immer derselbe falsche geprüft wird
    private int candidateOffset = 0;

    private final Set<String> reportedPorts = new HashSet<>();

    private boolean warnedNotFound = false;

    public PushToTalkSerial(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::supervise, 0, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        closePort();
    }

    // Läuft alle 50 ms: Verbindung prüfen, ggf. neu verbinden, Zeitüberschreitung überwachen
    private void supervise() {
        try {
            SerialPort port = serialPort;
            long now = System.currentTimeMillis();

            if (port == null) {
                if (now - lastConnectAttempt >= RECONNECT_INTERVAL_MS) {
                    lastConnectAttempt = now;
                    connect();
                }
                return;
            }

            // Manche Treiber melden das Abziehen nicht als Ereignis – dann liefert bytesAvailable() -1
            if (!port.isOpen() || port.bytesAvailable() < 0) {
                disconnect("serial port lost");
                return;
            }

            // Automatisch gefundener Port meldet sich nicht → nicht unser ESP, nächsten Kandidaten versuchen
            if (!confirmed && now - connectedAt > PROBE_TIMEOUT_MS) {
                // Jeden fremden Port nur einmal melden, sonst läuft die Konsole voll
                if (reportedPorts.add(port.getSystemPortName())) {
                    System.out.println("[PushToTalk] " + port.getSystemPortName() + " did not respond, not the ESP");
                }
                closePort();
                candidateOffset++;
                lastConnectAttempt = now;
                return;
            }

            long timeout = Settings.getPttTimeoutMs();
            if (timeout > 0 && pressed && now - lastMessageAt > timeout) {
                pressed = false;
                listener.onPushToTalkLost("no message from ESP for " + timeout + " ms");
            }
        } catch (RuntimeException e) {
            // Fehler dürfen den Überwachungs-Thread nicht beenden
            System.out.println("[PushToTalk] error: " + e.getMessage());
        }
    }

    private void connect() {
        SerialPort port = findPort();
        if (port == null) {
            if (!warnedNotFound) {
                System.out.println("[PushToTalk] ESP not found (" + Settings.getComPort()
                        + "), retrying in background. PushToTalk only available over console!");
                warnedNotFound = true;
            }
            return;
        }
        port.setBaudRate(115200);
        if (!port.openPort()) {
            // Port belegt (z. B. von anderer Software) → beim nächsten Versuch anderen Kandidaten nehmen
            candidateOffset++;
            if (!warnedNotFound) {
                System.out.println("[PushToTalk] unable to open " + port.getSystemPortName() + ", retrying in background");
                warnedNotFound = true;
            }
            return;
        }
        receivedData.setLength(0);
        pressed = false;
        connectedAt = System.currentTimeMillis();
        lastMessageAt = connectedAt;
        confirmed = isFixedPort();
        port.addDataListener(this);
        serialPort = port;
        if (confirmed) {
            warnedNotFound = false;
            System.out.println("[PushToTalk] connected to " + port.getSystemPortName() + " (" + port.getDescriptivePortName() + ")");
        }
    }

    private boolean isFixedPort() {
        String configured = Settings.getComPort();
        return configured != null && !configured.isBlank() && !configured.equalsIgnoreCase("auto");
    }

    private SerialPort findPort() {
        if (isFixedPort()) {
            // Fester Port – nur verwenden, wenn er gerade existiert
            for (SerialPort port : SerialPort.getCommPorts()) {
                if (port.getSystemPortName().equalsIgnoreCase(Settings.getComPort())) {
                    return port;
                }
            }
            return null;
        }

        List<SerialPort> candidates = new ArrayList<>();
        for (SerialPort port : SerialPort.getCommPorts()) {
            if (matchesFilter(port)) {
                candidates.add(port);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(Math.floorMod(candidateOffset, candidates.size()));
    }

    private boolean matchesFilter(SerialPort port) {
        List<Integer> vendorIds = Settings.getSerialVendorIds();
        if (!vendorIds.isEmpty() && !vendorIds.contains(port.getVendorID())) {
            return false;
        }
        int productId = Settings.getSerialProductId();
        if (productId >= 0 && port.getProductID() != productId) {
            return false;
        }
        String serialNumber = Settings.getSerialNumber();
        if (!serialNumber.isEmpty() && !serialNumber.equalsIgnoreCase(port.getSerialNumber())) {
            return false;
        }
        return true;
    }

    private void disconnect(String reason) {
        boolean wasConfirmed = confirmed;
        closePort();
        if (!wasConfirmed) {
            // War nur ein Kandidat in der Prüfung – kein echter Verbindungsverlust
            return;
        }
        System.out.println("[PushToTalk] disconnected: " + reason);
        pressed = false;
        listener.onPushToTalkLost(reason);
    }

    private void closePort() {
        SerialPort port = serialPort;
        serialPort = null;
        confirmed = false;
        if (port != null) {
            port.removeDataListener();
            port.closePort();
        }
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        SerialPort port = serialPort;
        if (port == null) {
            return;
        }
        if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            // Nicht im Callback-Thread von jSerialComm schließen, sondern an den Überwachungs-Thread abgeben
            scheduler.execute(() -> {
                if (serialPort == port) {
                    disconnect("ESP unplugged");
                }
            });
            return;
        }
        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            return;
        }

        int available = port.bytesAvailable();
        if (available <= 0) {
            return;
        }
        byte[] data = new byte[available];
        int read = port.readBytes(data, data.length);

        // Protokoll: "True"/"False", mit oder ohne Zeilenumbruch – kompatibel zur alten Firmware
        for (char c : new String(data, 0, Math.max(read, 0), StandardCharsets.US_ASCII).toCharArray()) {
            if (c == '\r' || c == '\n') {
                receivedData.setLength(0);
                continue;
            }
            receivedData.append(c);
            String command = receivedData.toString();
            if (command.equals("True") || command.equals("False")) {
                receivedData.setLength(0);
                lastMessageAt = System.currentTimeMillis();
                if (!confirmed) {
                    confirmed = true;
                    warnedNotFound = false;
                    System.out.println("[PushToTalk] ESP found on " + port.getSystemPortName()
                            + " (" + port.getDescriptivePortName() + ", serial number '" + port.getSerialNumber() + "')");
                }
                boolean signal = command.equals("True");
                boolean newPressed = signal != Settings.getInvertPushToTalk();
                // Nur Änderungen weitergeben – die Firmware wiederholt den Zustand zyklisch
                if (newPressed != pressed) {
                    pressed = newPressed;
                    listener.onPushToTalk(newPressed);
                }
            } else if (receivedData.length() > 16) {
                // Unbekannte Daten verwerfen, damit der Puffer nicht endlos wächst
                receivedData.setLength(0);
            }
        }
    }
}
