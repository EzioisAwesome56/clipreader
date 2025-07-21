package com.eziosoft.clipreader.objects;

import java.util.ArrayList;

public class Chunks {

    private final String layer_name;
    private final String chunk_filename;
    private final ArrayList<byte[]> bitmap_blocks;
    private final int layer_id;
    private byte[] offscreen_attrib;
    private final byte[] chunk_id;

    public int getLayer_id() {
        return this.layer_id;
    }

    public Chunks(String ln, String cf, ArrayList<byte[]> bitmap, int id, byte[] cid){
        this.layer_name = ln;
        this.chunk_filename = cf;
        this.bitmap_blocks = bitmap;
        this.layer_id = id;
        this.chunk_id = cid;
    }

    public byte[] getChunk_id() {
        return this.chunk_id;
    }

    public void setOffscreen_attrib(byte[] input){
        this.offscreen_attrib = input;
    }
    public byte[] getOffscreen_attrib() {
        return this.offscreen_attrib;
    }

    public String getLayer_name() {
        return this.layer_name;
    }

    public ArrayList<byte[]> getBitmap_blocks() {
        return this.bitmap_blocks;
    }

    public String getChunk_filename() {
        return this.chunk_filename;
    }
}
