package app.wolfware;

import java.io.Serializable;

public class Device implements Serializable {
    private String name;
    private int index;

    public Device(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }
}
