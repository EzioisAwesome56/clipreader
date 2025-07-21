package com.eziosoft;

import com.eziosoft.clipreader.ClipReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

public class LayerExportTest {

    public static void main(String[] args){
        System.out.println("Clipreader library test class: Layer Export");
        File output = new File("output");
        if (!output.exists()){
            output.mkdir();
        }
        try {
            ArrayList<BufferedImage> imgs = ClipReader.readClipFileLayers(new File(args[0]));
            int count = 0;
            for (BufferedImage bufimg : imgs){
                File f = new File(output, count + ".png");
                ImageIO.write(bufimg, "png", f);
                count++;
            }
        } catch (Exception e){
            System.out.println("Something broke");
            e.printStackTrace();
        }
    }
}
