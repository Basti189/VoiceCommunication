package app.wolfware.client;

import app.wolfware.Audio;
import app.wolfware.Device;
import app.wolfware.Settings;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.OutputStream;
import java.net.Socket;

public class MicrophoneClient implements Runnable, SerialPortDataListener {

    public final Device microphone;

    private volatile boolean stop = false;

    private volatile boolean isMute = true;

    private SerialPort serialPort;

    private StringBuilder receivedData = new StringBuilder();

    public MicrophoneClient(Device microphone) {
        this.microphone = microphone;
    }

    @Override
    public void run() {
        Audio audio = new Audio();
        audio.getInputLine(microphone.getIndex());
        audio.startInputLine();
        isMute = Settings.getPushToTalk();

        if (Settings.getPushToTalk()) {
            System.out.println("[MicrophoneClient] try to open " + Settings.getComPort());
            serialPort = SerialPort.getCommPort(Settings.getComPort());
            serialPort.setBaudRate(115200);
            if (serialPort.openPort()) {
                serialPort.addDataListener(this);
                System.out.println("[MicrophoneClient] Port is opened and ready to use");
            } else {
                System.out.println("[MicrophoneClient] Unable to open " + Settings.getComPort() + ". PushToTalk only available over console!");
                serialPort = null;
            }
        }

        while (!stop) {
            Socket socket = null;
            try {
                Thread.sleep(1000);
                System.out.println("[MicrophoneClient] connecting to " + Settings.getIp() + ":" + Settings.getPort());
                socket = new Socket(Settings.getIp(), Settings.getPort());
                System.out.println("[MicrophoneClient] Connected!");
                Thread.sleep(1000);
                OutputStream out = socket.getOutputStream();

                while (!stop) {
                    if (!isMute) {
                        byte[] buffer = new byte[1024];
                        audio.getTargetLine().read(buffer, 0, buffer.length);
                        out.write(buffer, 0, buffer.length);
                    }
                }

            } catch (Exception ignore) {
                //System.out.println("[MicrophoneClient] Reconnecting...");
                //throw new RuntimeException(e);
            }
        }
        if (serialPort != null) {
            serialPort.closePort();
        }
        System.out.println("[MicrophoneClient] shutdown");
    }

    public void stop() {
        stop = true;
    }

    public void setMute(boolean isMute) {
        if (isMute) {
            System.out.println("[MicrophoneClient] microphone muted");
        } else {
            System.out.println("[MicrophoneClient] you can speak now");
        }
        this.isMute = isMute;
    }

    public void toggleMute() {
        setMute(!isMute);
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        // Receive Data from ESP (Arduino) for push to talk
        if (serialPortEvent.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            byte[] newData = new byte[serialPort.bytesAvailable()];
            int numRead = serialPort.readBytes(newData, newData.length);

            receivedData.append(new String(newData));

            if (receivedData.toString().equals("True")) {
                setMute(Settings.getInvertPushToTalk());

                receivedData.setLength(0);
            } else if (receivedData.toString().equals("False")) {
                setMute(!Settings.getInvertPushToTalk());
               receivedData.setLength(0);
            }
        }
    }
}
