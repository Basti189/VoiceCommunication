package app.wolfware;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Audio {
    private AudioFormat audioformat;
    private SourceDataLine sourceLine;
    private TargetDataLine targetLine;
    private Mixer.Info[] mixerInfo;
    private Mixer input;
    private Mixer output;
    private DataLine.Info inputInfo;
    private DataLine.Info outputInfo;

    public Audio() {
        float sampleRate = 44100;
        int sampleSizeInBits = 16;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = false;
        this.audioformat = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        this.mixerInfo = AudioSystem.getMixerInfo();
        inputInfo = new DataLine.Info(TargetDataLine.class, this.audioformat);
        outputInfo = new DataLine.Info(SourceDataLine.class, this.audioformat);
    }

    //---------------------------------------------------------------------------------------------------------
    public void getInputLine(int index){
        try {
            input = AudioSystem.getMixer(mixerInfo[index]);
            targetLine = (TargetDataLine) input.getLine(inputInfo);
        } catch (LineUnavailableException ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void startInputLine(){
        try {
            targetLine.open(audioformat);
            targetLine.start();
        } catch (LineUnavailableException ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void getOutputLine(int index){
        try {
            output = AudioSystem.getMixer(mixerInfo[index]);
            sourceLine = (SourceDataLine) output.getLine(outputInfo);
        } catch (LineUnavailableException ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void startOutputLine(){
        try {
            sourceLine.open(audioformat);
            sourceLine.start();
        } catch (LineUnavailableException ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public List<Device> getInputDevice(){
        List<Device> list = new ArrayList<>();
        for(int i = 0; i < mixerInfo.length; i++){
            Mixer tmp = AudioSystem.getMixer(mixerInfo[i]);
            if(tmp.isLineSupported(inputInfo)){
                list.add(new Device(mixerInfo[i].getName(), i));
            }
        }
        return list;
    }

    public List<Device> getOutputDevice(){
        List<Device> list = new ArrayList<>();
        for(int i = 0; i < mixerInfo.length; i++){
            Mixer tmp = AudioSystem.getMixer(mixerInfo[i]);
            if(tmp.isLineSupported(outputInfo)){
                list.add(new Device(mixerInfo[i].getName(), i));
            }
        }
        return list;
    }

    public void setVolume(int level) {
        if (level > 100) {
            level = 100;
        } else if (level < 0) {
            level = 0;
        }
        FloatControl control = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
        float v = (float) (control.getMinimum() + (level / 100.0) * (control.getMaximum() - control.getMinimum()));
        control.setValue(v);
    }

    public AudioFormat getAudioformat() {
        return audioformat;
    }

    public void setAudioformat(AudioFormat audioformat) {
        this.audioformat = audioformat;
    }

    public SourceDataLine getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(SourceDataLine sourceLine) {
        this.sourceLine = sourceLine;
    }

    public TargetDataLine getTargetLine() {
        return targetLine;
    }

    public void setTargetLine(TargetDataLine targetLine) {
        this.targetLine = targetLine;
    }

    public Mixer.Info[] getMixerInfo() {
        return mixerInfo;
    }

    public void setMixerInfo(Mixer.Info[] mixerInfo) {
        this.mixerInfo = mixerInfo;
    }

    public Mixer getInput() {
        return input;
    }

    public void setInput(Mixer input) {
        this.input = input;
    }

    public Mixer getOutput() {
        return output;
    }

    public void setOutput(Mixer output) {
        this.output = output;
    }

    public DataLine.Info getInputInfo() {
        return inputInfo;
    }

    public void setInputInfo(DataLine.Info inputInfo) {
        this.inputInfo = inputInfo;
    }

    public DataLine.Info getOutputInfo() {
        return outputInfo;
    }

    public void setOutputInfo(DataLine.Info outputInfo) {
        this.outputInfo = outputInfo;
    }
}
