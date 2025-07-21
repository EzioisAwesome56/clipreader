package com.eziosoft.clipreader.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class zlibUtils {

    /**
     * python has a simple zlib.decompress, which is used in the script.
     * java has more work required, so this code will do that
     * @param input data to decompress
     * @return decompressed byte array, null if it failed
     */
    public static byte[] zlibDecompressData(byte[] input)
    {
        try {
            // setup our crap
            Inflater inflate = new Inflater();
            inflate.setInput(input);
            // create output stream
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            // we also need a buffer of some sort
            byte[] buffer = new byte[512];
            // decompress
            while (!inflate.finished()) {
                int extract_size = inflate.inflate(buffer);
                stream.write(buffer, 0, extract_size);
            }
            // once we're done, return our data
            return stream.toByteArray();
        } catch (DataFormatException e){
            e.printStackTrace();
            return null;
        }
    }
}
