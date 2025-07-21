package com.eziosoft.clipreader.objects;

public class OffscreenChunksInfo {

    private int mainid;
    private int layerid;
    private byte[] blockData;
    private byte[] attribute;

    public int getMainid() {
        return this.mainid;
    }

    public byte[] getAttribute() {
        return this.attribute;
    }

    public byte[] getBlockData() {
        return this.blockData;
    }

    public int getLayerid() {
        return this.layerid;
    }

    public OffscreenChunksInfo(int id, int layerid, byte[] blockData, byte[] attribute){
        this.mainid = id;
        this.layerid = layerid;
        this.blockData = blockData;
        this.attribute = attribute;
    }
}
