package app.wolfware;

public class Device {

    // Systemstandard: kein festes Gerät, sondern das, was das Betriebssystem gerade als Standard nutzt
    public static final int SYSTEM_DEFAULT = -2;

    // Gerät nicht verwenden
    public static final int DISABLED = -1;

    // Name, unter dem das Gerät in der Konfiguration steht
    private final String name;

    private final int index;

    // Zusatzinfo nur für die Anzeige (Treiberbeschreibung), nicht in der Konfiguration
    private final String description;

    // true, wenn das Gerät nur ersatzweise gewählt wurde, weil das konfigurierte fehlt
    private final boolean fallback;

    public Device(String name, int index) {
        this(name, index, "", false);
    }

    public Device(String name, int index, String description) {
        this(name, index, description, false);
    }

    private Device(String name, int index, String description, boolean fallback) {
        this.name = name;
        this.index = index;
        this.description = description;
        this.fallback = fallback;
    }

    public static Device systemDefault() {
        return systemDefault("");
    }

    public static Device systemDefault(String resolvedName) {
        String description = "Systemstandard - umschaltbar in den Sound-Einstellungen";
        if (resolvedName != null && !resolvedName.isEmpty()) {
            description += " (zurzeit: " + resolvedName + ")";
        }
        return new Device("default", SYSTEM_DEFAULT, description);
    }

    public Device asFallback() {
        return new Device(name, index, description, true);
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFallback() {
        return fallback;
    }
}
