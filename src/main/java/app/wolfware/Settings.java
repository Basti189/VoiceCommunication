package app.wolfware;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Settings {

    private final static String configFilePath = "./client.cfg";

    private static String speaker = "";

    private static String microphone = "";

    private static Integer loudness = 50;

    private static Integer port = 5000;

    private static String ip = "127.0.0.1";

    private static Boolean pushToTalk = false;

    private static String comPort = "COM1";

    public static void init() {
        try {
            FileInputStream fis = new FileInputStream(configFilePath);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            Properties props = new Properties();
            props.load(isr);

            speaker = props.getProperty("speaker");
            microphone = props.getProperty("microphone");
            loudness = Integer.parseInt(props.getProperty("loudness"));
            port = Integer.parseInt(props.getProperty("port"));
            ip = props.getProperty("ip");
            pushToTalk = Boolean.parseBoolean(props.getProperty("pushToTalk"));
            comPort = props.getProperty("comPort");

            isr.close();
            fis.close();
        } catch (FileNotFoundException fnf) {
            System.out.println("No config");
        } catch (IOException ioe) {

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
        props.setProperty("comPort", comPort);

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

    public static String getComPort() {
        return comPort;
    }

    public static void setComPort(String comPort) {
        Settings.comPort = comPort;
    }
}