package com.eziosoft.clipreader.objects;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

public class LayerObject {

    private final boolean is_folder;
    private final HashMap<String, Object> layer_data;
    private final int layer_id;
    private final String layer_name;
    // mipmap crap
    private String render_mipmap;
    private String maskmipmap;

    public boolean isIs_folder() {
        return this.is_folder;
    }

    public String getLayer_name() {
        return this.layer_name;
    }

    public int getLayer_id() {
        return this.layer_id;
    }

    public HashMap<String, Object> getLayer_data() {
        return this.layer_data;
    }

    public String getRender_mipmap() {
        return this.render_mipmap;
    }

    public void setRender_mipmap(String render_mipmap) {
        this.render_mipmap = render_mipmap;
    }

    public String getMaskmipmap() {
        return this.maskmipmap;
    }

    public void setMaskmipmap(String maskmipmap) {
        this.maskmipmap = maskmipmap;
    }

    public LayerObject(boolean folder, HashMap data, int id, String name){
        this.is_folder = folder;
        this.layer_data = data;
        this.layer_id = id;
        this.layer_name = name;
        this.maskmipmap = null;
        this.render_mipmap = null;
    }
}
