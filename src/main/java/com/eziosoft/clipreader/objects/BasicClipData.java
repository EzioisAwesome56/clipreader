package com.eziosoft.clipreader.objects;

import java.util.ArrayList;

public class BasicClipData {

    private final SqliteData sqldata;
    private final ArrayList<FileChunk> fileChunks;


    public BasicClipData(SqliteData sqldata, ArrayList<FileChunk> files){
        this.sqldata = sqldata;
        this.fileChunks = files;
    }

    public SqliteData getSqldata() {
        return this.sqldata;
    }

    public ArrayList<FileChunk> getFileChunks() {
        return this.fileChunks;
    }
}
