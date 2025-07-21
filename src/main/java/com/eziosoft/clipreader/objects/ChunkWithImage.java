package com.eziosoft.clipreader.objects;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class ChunkWithImage {

    private final String layer_name;
    private final int layer_id;
    private final String chunk_id;
    private final BufferedImage image;
    private final boolean monochrome;
    private ArrayList<BufferedImage> masks;
    private HashMap<String, Object> attribs;

    public boolean isMonochrome() {
        return this.monochrome;
    }

    public ChunkWithImage(Chunks chunk, BufferedImage img, boolean monochrome, HashMap map){
        this.layer_name = chunk.getLayer_name();
        this.layer_id = chunk.getLayer_id();
        this.chunk_id = new String(chunk.getChunk_id(), StandardCharsets.UTF_8);
        this.image = img;
        this.monochrome = monochrome;
        // TODO: this may actually break things on actual monochrome images
        //      but this is the best i can come up with for mask detection
        if (!this.monochrome){
            this.masks = new ArrayList<>();
        }
        this.attribs = map;
    }

    public void add_mask(BufferedImage mask){
        this.masks.add(mask);
    }

    public ArrayList<BufferedImage> getMasks() {
        return this.masks;
    }

    public String getLayer_name() {
        return this.layer_name;
    }

    public String getChunk_id() {
        return this.chunk_id;
    }

    public BufferedImage getImage() {
        return this.image;
    }

    public int getLayer_id() {
        return this.layer_id;
    }
    public HashMap<String, Object> getAttribs() {
        return this.attribs;
    }
}
