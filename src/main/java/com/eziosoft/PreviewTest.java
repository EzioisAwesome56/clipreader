package com.eziosoft;

import com.eziosoft.clipreader.ClipReader;
import com.eziosoft.clipreader.objects.CSPPreviewWithMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class PreviewTest {

    /**
     * this is entirely for testing the library. do not use regularly, obviously
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Clipreader library test class");
        BufferedImage hi;
        try {
            CSPPreviewWithMeta meta = ClipReader.readClipFilePreview(new File(args[0]));
            System.out.println("Height: " + meta.getHeight());
            System.out.println("Width: " + meta.getWidth());
            hi = meta.getImg();
            ImageIO.write(hi, "png", new File("myfile.png"));
        } catch (Exception e){
            System.out.println("Something broke");
            e.printStackTrace();
        }
    }
}