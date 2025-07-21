package com.eziosoft;

import com.eziosoft.clipreader.ClipReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class RebuildTest {

    public static void main(String[] args){
        System.out.println("Clipreader library test class: rebuilder");
        BufferedImage hi;
        try {
            hi = ClipReader.readClipFile(new File(args[0]));
            File test = new File("test.png");
            ImageIO.write(hi, "png", test);
        } catch (Exception e){
            System.out.println("Something broke");
            e.printStackTrace();
        }
    }
}
