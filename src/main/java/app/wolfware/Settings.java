package app.wolfware;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Settings {

    private final static String configFilePath = "./client.cfg";

    private static String speaker = "";

    private static String microphone = "";

    private static Integer loudness = 50;

    private static Integer port = 5000;

    private static String ip = "127.0.0.1";

    private static Boolean pushToTalk = false;

    private static Boolean invertPushToTalk = false;

    // Halbduplex: Während man selbst sendet, wird der eigene Lautsprecher stummgeschaltet (gegen Echo/Rückkopplung)
    private static Boolean halfDuplex = false;

    // "auto" = ESP über USB-Hersteller-ID suchen, sonst fester Port wie "COM3"
    private static String comPort = "auto";

    // Erlaubte USB-Hersteller-IDs (hex, kommagetrennt): 10C4 = CP210x (NodeMCU), 1A86 = CH340,
    // 303A = Espressif (natives USB des ESP32-S3). Leer = alle Ports prüfen
    private static List<Integer> serialVendorIds = List.of(0x10C4, 0x1A86, 0x303A);

    // Optionale Seriennummer des USB-Wandlers, um genau einen ESP festzulegen (CP210x hat eine eindeutige)
    private static String serialNumber = "";

    // Optionale Produkt-ID (hex), leer = beliebig
    private static Integer serialProductId = -1;

    // Mikrofon stumm schalten, wenn der ESP so lange nichts sendet (0 = aus, nur mit zyklisch sendender Firmware nutzen)
    private static Integer pttTimeoutMs = 0;

    public static void init() {
        try {
            FileInputStream fis = new FileInputStream(configFilePath);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            Properties props = new Properties();
            props.load(isr);

            // Mit Standardwerten lesen, damit fehlende Einträge nicht zum Absturz führen
            speaker = props.getProperty("speaker", speaker);
            microphone = props.getProperty("microphone", microphone);
            loudness = Integer.parseInt(props.getProperty("loudness", String.valueOf(loudness)));
            port = Integer.parseInt(props.getProperty("port", String.valueOf(port)));
            ip = props.getProperty("ip", ip);
            pushToTalk = Boolean.parseBoolean(props.getProperty("pushToTalk", String.valueOf(pushToTalk)));
            invertPushToTalk = Boolean.parseBoolean(props.getProperty("invertPushToTalk", String.valueOf(invertPushToTalk)));
            halfDuplex = Boolean.parseBoolean(props.getProperty("halfDuplex", String.valueOf(halfDuplex)));
            comPort = props.getProperty("comPort", comPort);
            serialVendorIds = parseHexList(props.getProperty("serialVendorId"), serialVendorIds);
            serialNumber = props.getProperty("serialNumber", serialNumber).trim();
            serialProductId = parseHex(props.getProperty("serialProductId"), serialProductId);
            pttTimeoutMs = Integer.parseInt(props.getProperty("pttTimeoutMs", String.valueOf(pttTimeoutMs)));

            isr.close();
            fis.close();
        } catch (FileNotFoundException fnf) {
            System.out.println("No config");
        } catch (IOException | NumberFormatException e) {
            System.out.println("Invalid config: " + e.getMessage());
        }
    }

    public static void save() {
        Properties props = new Properties();

        props.setProperty("speaker", speaker);
        props.setProperty("microphone", microphone);
        props.setProperty("loudness", String.valueOf(loudness));
        props.setProperty("port", String.valueOf(port));
        props.setProperty("ip", ip);
        props.setProperty("pushToTalk", String.valueOf(pushToTalk));
        props.setProperty("invertPushToTalk", String.valueOf(invertPushToTalk));
        props.setProperty("halfDuplex", String.valueOf(halfDuplex));
        props.setProperty("comPort", comPort);
        props.setProperty("serialVendorId", String.join(",", serialVendorIds.stream().map(id -> String.format("%04X", id)).toList()));
        props.setProperty("serialNumber", serialNumber);
        props.setProperty("serialProductId", serialProductId < 0 ? "" : String.format("%04X", serialProductId));
        props.setProperty("pttTimeoutMs", String.valueOf(pttTimeoutMs));

        Writer fstream = null;
        BufferedWriter out = null;
        try {
            fstream = new OutputStreamWriter(new FileOutputStream(new File(configFilePath)), StandardCharsets.UTF_8);
            //FileWriter writer = new FileWriter(new File(configFilePath));
            props.store(fstream, "settings");
            fstream.close();
            //writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSpeaker() {
        return speaker;
    }

    public static void setSpeaker(String speaker) {
        Settings.speaker = speaker;
        save();
    }

    public static String getMicrophone() {
        return microphone;
    }

    public static void setMicrophone(String microphone) {
        Settings.microphone = microphone;
        save();
    }

    public static Integer getLoudness() {
        return loudness;
    }

    public static void setLoudness(Integer loudness) {
        Settings.loudness = loudness;
        save();
    }

    public static Integer getPort() {
        return port;
    }

    public static void setPort(Integer port) {
        Settings.port = port;
    }

    public static String getIp() {
        return ip;
    }

    public static void setIp(String ip) {
        Settings.ip = ip;
    }

    public static Boolean getPushToTalk() {
        return pushToTalk;
    }

    public static void setPushToTalk(Boolean pushToTalk) {
        Settings.pushToTalk = pushToTalk;
    }

    public static Boolean getInvertPushToTalk() {
        return invertPushToTalk;
    }

    public static void setInvertPushToTalk(Boolean invertPushToTalk) {
        Settings.invertPushToTalk = invertPushToTalk;
    }

    public static String getComPort() {
        return comPort;
    }

    public static void setComPort(String comPort) {
        Settings.comPort = comPort;
    }

    public static Boolean getHalfDuplex() {
        return halfDuplex;
    }

    public static void setHalfDuplex(Boolean halfDuplex) {
        Settings.halfDuplex = halfDuplex;
    }

    public static List<Integer> getSerialVendorIds() {
        return serialVendorIds;
    }

    public static String getSerialNumber() {
        return serialNumber;
    }

    public static Integer getSerialProductId() {
        return serialProductId;
    }

    public static Integer getPttTimeoutMs() {
        return pttTimeoutMs;
    }

    private static List<Integer> parseHexList(String value, List<Integer> fallback) {
        if (value == null) {
            return fallback;
        }
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            int id = parseHex(part, -1);
            if (id >= 0) {
                result.add(id);
            }
        }
        return result;
    }

    private static Integer parseHex(String value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return -1;
        }
        if (value.toLowerCase().startsWith("0x")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value, 16);
    }
}
