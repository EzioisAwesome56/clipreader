package com.eziosoft.clipreader.objects;

public class MipmapInfoObject {

    private final int mainid;
    private final int offscreen;

    public MipmapInfoObject(int main, int offscreen){
        this.mainid = main;
        this.offscreen = offscreen;
    }

    public int getMainid() {
        return this.mainid;
    }

    public int getOffscreen() {
        return this.offscreen;
    }
}
