package com.eziosoft.clipreader.objects;

import java.util.ArrayList;
import java.util.HashMap;

public class SqliteData {

    private final ArrayList<OffscreenChunksInfo> offscreen;
    private final ArrayList<HashMap> layer;
    private final int width;
    private final int height;
    private final int dpi;
    private final byte[] canvas_preview;
    private final int root_folder;
    // turns out we need mipmaps for some stuff
    private final ArrayList<MipmapObject> mipmap;
    private final ArrayList<MipmapInfoObject> mipmapinfo;

    public int getRoot_folder() {
        return this.root_folder;
    }

    public SqliteData(ArrayList offscreen, ArrayList layer, int w, int h, int dpi, byte[] preview, int root, ArrayList mipmap, ArrayList mipmapinfo){
        this.offscreen = offscreen;
        this.layer = layer;
        this.width = w;
        this.height = h;
        this.dpi = dpi;
        this.canvas_preview = preview;
        this.root_folder = root;
        this.mipmap = mipmap;
        this.mipmapinfo = mipmapinfo;
    }

    public ArrayList<OffscreenChunksInfo> getOffscreen() {
        return this.offscreen;
    }

    public int getDpi() {
        return this.dpi;
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }

    public ArrayList<HashMap> getLayer() {
        return this.layer;
    }

    public byte[] getCanvas_preview() {
        return this.canvas_preview;
    }

    public ArrayList<MipmapObject> getMipmap() {
        return this.mipmap;
    }

    public ArrayList<MipmapInfoObject> getMipmapinfo() {
        return this.mipmapinfo;
    }
}
