package com.eziosoft.clipreader.objects;

import java.nio.charset.StandardCharsets;

public class FileChunk {

    // from the basic port, these are all the datatypes we need
    private byte[] chunk_name;
    // NOTE: this was originally a python memory view
    private byte[] data;
    private int offset;

    public FileChunk(byte[] name, byte[] data, int offset){
        this.chunk_name = name;
        this.data = data;
        this.offset = offset;
    }

    public byte[] getChunk_name() {
        return this.chunk_name;
    }
    public String getNameString(){
        return new String(this.chunk_name, StandardCharsets.UTF_8);
    }

    public int getOffset() {
        return this.offset;
    }

    public byte[] getData() {
        return this.data;
    }
}
