package com.eziosoft.clipreader.objects;

import java.awt.image.BufferedImage;

public class CSPPreviewWithMeta {

    private BufferedImage img;
    private int height;
    private int width;

    public CSPPreviewWithMeta(BufferedImage out, int h, int w){
        this.img = out;
        this.height = h;
        this.width = w;
    }

    public BufferedImage getImg() {
        return this.img;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}
