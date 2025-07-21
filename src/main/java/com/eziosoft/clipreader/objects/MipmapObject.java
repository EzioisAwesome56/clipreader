package com.eziosoft.clipreader.objects;

public class MipmapObject {

    private final int mainid;
    private final int basemipmapinfo;

    public MipmapObject(int main, int info){
        this.mainid = main;
        this.basemipmapinfo = info;
    }

    public int getMainid() {
        return this.mainid;
    }

    public int getBasemipmapinfo() {
        return this.basemipmapinfo;
    }
}
