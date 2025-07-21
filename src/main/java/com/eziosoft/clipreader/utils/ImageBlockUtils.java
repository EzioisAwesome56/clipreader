package com.eziosoft.clipreader.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageBlockUtils {


    /**
     * java's imageio cant read in raw pixel data unlike python's PIL class
     * to work around this, this method will take in an array of pixels
     * and return a 256x256 image
     * @param rgb pixel data
     * @return buffered image
     */
    public static BufferedImage parseBitmapBlock(byte[] rgb, byte[] alpha){
        // create our new buffered image
        BufferedImage block = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        // for convience, we will make a stream for reading from
        ByteArrayInputStream rgbstream = new ByteArrayInputStream(rgb);
        ByteArrayInputStream alphastream = new ByteArrayInputStream(alpha);
        for (int y = 0; y < 256; y++){
            for (int x = 0; x < 256; x++){
                // get pixel data
                int[] pixel = readBGRU(rgbstream);
                // make sure its valid, if not bail
                if (pixel == null) return null;
                // also read alpha
                int alpha_val = readAlpha(alphastream);
                if (alpha_val == -1) return null;
                // update pixel
                // script suggests its actually encoded as: bgr, with alpha seperate
                // FIXME: works better for somethings as 2, 1, 0
                //          2, 0, 1 for others
                block.setRGB(x, y, new Color(pixel[2], pixel[1], pixel[0], alpha_val).getRGB());
            }
        }
        // should be done by now then
        return block;
    }

    /**
     * Parsed a bitmap block set to grayscale mode, and returns it to be used like normal
     * @param bytes bitmap block to use
     * @return drawn image, or null if something failed
     */
    public static BufferedImage parseGrayscaleBitmapBlock(byte[] bytes){
        // create new buffered image
        BufferedImage block = new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_GRAY);
        // make an input stream
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        for (int y = 0; y < 256; y++){
            for (int x = 0; x < 256; x++){
                // obtain a byte of data
                int data = readByte(stream);
                // fail if its invalid
                if (data == -1) return null;
                // otherwise, update the pixel
                block.setRGB(x, y, new Color(data, 0, 0).getRGB());
            }
        }
        // and we're done
        return block;
    }

    /**
     * reads 4 ints from a stream and then returns an array.
     * is atleast BGR, with some forth unknown value
     * @param stream stream to read from
     * @return int[], or null if something failed
     */
    private static int[] readBGRU(InputStream stream){
        // make new array
        try {
            int[] temp = new int[4];
            for (int i = 0; i < 4; i++) {
                int read = stream.read();
                if (read == -1){
                    System.out.println("Error: ran out of buffer");
                    return null;
                }
                temp[i] = read;
            }
            // return this
            return temp;
        } catch (IOException e){
            System.out.println("Error reading stream for pixels");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * reads an input stream for a singular byte, returns -1 if anything breaks
     * @param stream input stream to read from
     * @return value, or -1 if failure
     */
    private static int readByte(InputStream stream){
        // this is pretty much a copy paste from the readAlpha function,
        // but its so simple can you really blame me?
        try {
            return stream.read();
        } catch (IOException e){
            System.out.println("Error reading stream for data");
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * reads a singular int from the provided stream
     * it should be alpha data
     * @param stream stream of alpha data
     * @return singular int, or -1 if reading failed
     */
    private static int readAlpha(InputStream stream){
        // do the things
        try {
            return stream.read();
        } catch (IOException e){
            System.out.println("Error reading stream for alpha");
            e.printStackTrace();
            return -1;
        }
    }
}
