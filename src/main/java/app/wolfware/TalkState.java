package app.wolfware;

/**
 * Gemeinsamer Zustand zwischen Mikrofon und Lautsprecher (für den Halbduplex-Betrieb).
 */
public final class TalkState {

    private static volatile boolean transmitting = false;

    private TalkState() {
    }

    public static boolean isTransmitting() {
        return transmitting;
    }

    public static void setTransmitting(boolean transmitting) {
        TalkState.transmitting = transmitting;
    }
}
