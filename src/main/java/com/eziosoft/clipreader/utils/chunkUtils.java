package com.eziosoft.clipreader.utils;

import com.eziosoft.clipreader.ClipReader;
import com.eziosoft.clipreader.objects.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class chunkUtils {

    /**
     * port of "parse_chunk_with_blocks" from the original script
     * @param data chunk data to parse
     */
    public static ArrayList<byte[]> parseChunkWithBlocks(byte[] data){
        // temp variables for shuffling data around
        byte[] tempbyte;
        byte[] tempbyte2;
        String tempstring;
        ByteBuffer tempbuf;
        // random magic numbers for some reason
        int ii = 0;
        int block_count1 = 0;
        // init the place to put all the found blocks
        ArrayList<byte[]> bitmap_blocks = new ArrayList<>();
        // loop
        while (ii < data.length){
            int block_size = 0;
            int fuck;
            // do some dumb array splitting crap
            fuck = (ii + 4 + ClipReader.BlockStatus.length) - ii;
            tempbyte = new byte[fuck];
            System.arraycopy(data, ii, tempbyte, 0, fuck);
            // MORE OF IT WHY DOES PYTHON HAVE IT EASIER
            fuck = (ii + 8 + ClipReader.BlockDataBeginChunk.length) - (ii + 8);
            tempbyte2 = new byte[fuck];
            System.arraycopy(data, ii + 8, tempbyte2, 0, fuck);
            // convert that shit to a string
            tempstring = new String(tempbyte, StandardCharsets.UTF_16BE);
            String tempstring3 = new String(tempbyte2, StandardCharsets.UTF_16BE);
            // im a dumbass; we need to check for the checksum using different math also
            fuck = (ii + 4 + ClipReader.BlockCheckSum.length) - ii;
            tempbyte2 = new byte[fuck];
            System.arraycopy(data, ii, tempbyte2, 0, fuck);
            String tempstring4 = new String(tempbyte2, StandardCharsets.UTF_16BE);
            if (tempstring.equals(new String(new byte[]{0x0, 0x0, 0x0, 0x0b}, StandardCharsets.UTF_16BE) + ClipReader.bs_string)){
                System.out.println("BlockStatus Block");
                // i am going to explode if i have to keep doing this
                fuck = (ii + 30 + 4) - (ii + 26 + 4);
                tempbyte2 = new byte[fuck];
                System.arraycopy(data, ii + 26 + 4, tempbyte2, 0, fuck);
                tempbuf = ByteBuffer.wrap(tempbyte2);
                tempbuf.order(ByteOrder.BIG_ENDIAN);
                int status_count = tempbuf.getInt();
                // delete the buffer
                tempbuf.clear();
                // check it
                if (block_count1 != status_count) System.out.println("Mismatch in block count while parsing layers");
                // finally, update block size
                block_size = (status_count * 4) + 12 + (ClipReader.BlockStatus.length + 4);
            } else if (tempstring4.equals(new String(new byte[]{0x0, 0x0, 0x0, 0x0d}, StandardCharsets.UTF_16BE) + ClipReader.bcs_string)){
                System.out.println("BlockCheckSum block");
                block_size = 4 + ClipReader.BlockCheckSum.length + 12 + (block_count1 * 4);
            } else if (tempstring3.equals(ClipReader.bdbc_string)){
                System.out.println("BlockDataBeginChunk block");
                // oh boy more array splitting i am going insane
                fuck = (ii + 4) - ii;
                tempbyte = new byte[fuck];
                System.arraycopy(data, ii, tempbyte, 0, fuck);
                // now we need an int. augh
                tempbuf = ByteBuffer.wrap(tempbyte);
                tempbuf.order(ByteOrder.BIG_ENDIAN);
                // update block size
                block_size = tempbuf.getInt();
                tempbuf.clear();
                // now we have to read some shit from the end
                fuck = (ii + block_size) - (ii + block_size - (4 + ClipReader.BlockDataEndChunk.length));
                byte[] read_data = new byte[fuck];
                System.arraycopy(data, ii + block_size - (4 + ClipReader.BlockDataEndChunk.length), read_data, 0, fuck);
                String debug1 = new String(read_data, StandardCharsets.UTF_16BE);
                String debug2 = new String(new byte[]{0x0, 0x0, 0x0, 0x11}, StandardCharsets.UTF_16BE) + ClipReader.bdec_string;
                if (!debug1.equals(debug2)){
                    System.out.println("ERROR: Failed to parse bitmap chunk");
                    return null;
                }
                // get the block now
                fuck = (ii + block_size - (4 + ClipReader.BlockDataEndChunk.length)) - (ii + 8 + ClipReader.BlockDataBeginChunk.length);
                byte[] block = new byte[fuck];
                System.arraycopy(data, (ii + 8 + ClipReader.BlockDataBeginChunk.length), block, 0, fuck);
                // now we have to even more copying
                fuck = (4 * 5) - (4 * 4);
                tempbyte = new byte[fuck];
                System.arraycopy(block, 4 * 4, tempbyte, 0, fuck);
                // get int
                tempbuf = ByteBuffer.wrap(tempbyte);
                tempbuf.order(ByteOrder.BIG_ENDIAN);
                int has_data = tempbuf.getInt();
                tempbuf.clear();
                // sanity checks i think
                if (!((0 <= has_data) || (has_data <= 1))){
                    System.out.println("Error parsing bitmap block");
                    return null;
                }
                // python treats > 0 as true, so
                if (has_data > 0){
                    // get the subblock length
                    fuck = (6 * 4) - (5 * 4);
                    tempbyte = new byte[fuck];
                    System.arraycopy(block, (5 * 4), tempbyte, 0, fuck);
                    // then, convert to int
                    tempbuf = ByteBuffer.wrap(tempbyte);
                    tempbuf.order(ByteOrder.BIG_ENDIAN);
                    int subblock_len = tempbuf.getInt();
                    // clear the buffer
                    tempbuf.clear();
                    // sanity checks
                    if (!(block.length == (subblock_len + (4*6)))){
                        System.out.println("ERROR: cant parse bitmap chunk, invalid block format");
                        return null;
                    }
                    // otherwise, get the data
                    fuck = block.length - (7 * 4);
                    tempbyte = new byte[fuck];
                    System.arraycopy(block, (7 * 4), tempbyte, 0, fuck);
                    // append to our list of bitmap blocks
                    bitmap_blocks.add(tempbyte);
                } else {
                    // TODO: apparently we just give up and fail? wtf?
                    bitmap_blocks.add(null);
                }
                block_count1 += 1;
            } else {
                System.out.println("ERROR: unknown block format");
                return null;
            }


            // TODO: the rest of this fucking loop
            ii += block_size;
        }
        // final sanity check for this
        if (ii != data.length){
            System.out.println("WARN: invalid last block size, overflow " + data.length + " by " + ii);
        }
        return bitmap_blocks;
    }

    /**
     * this is not a port of anything, instead me taking shortcuts
     * we need to apply each offscreen attribute to a data chunk, so this will do so
     * @param chunks chunks to process
     * @param sql sqldata needed to pull data to apply from
     */
    public static void applyOffscreenAttribsToChunks(ArrayList<Chunks> chunks, SqliteData sql){
        // loop thru all the chunks
        for (Chunks chunk : chunks){
            byte[] attrib = null;
            String chunkid = new String(chunk.getChunk_id(), StandardCharsets.UTF_8);
            // look for the chunkid we need
            for (OffscreenChunksInfo info : sql.getOffscreen()){
                // convert to a string
                String infoname = new String(info.getBlockData(), StandardCharsets.UTF_8);
                if (infoname.equals(chunkid)){
                    System.out.println("Found attribute for chunk " + chunkid);
                    attrib = info.getAttribute();
                    break;
                }
            }
            // sanity checks
            if (attrib != null){
                // apply it to the chunk
                chunk.setOffscreen_attrib(attrib);
            } else {
                System.out.println("WARN: no attribute found for chunk: " + chunkid);
            }
        }
    }

    /**
     * rough port of "decode_to_img" from the original script
     * @param chunk chunk to decode image data from
     */
    public static ChunkWithImage decodeChunkToImage(Chunks chunk){
        ImageIO.setUseCache(false);
        // first we need to parse the offscreen attributes
        HashMap<String, Object> attribs = sqliteUtils.parseOffscreenAttributesSqlValue(chunk.getOffscreen_attrib());
        // FIXME: the original test file from the source repo is malformed? we have to work around this
        if (attribs == null){
            System.out.println("Error: chunk attribs are null, skipping");
            return null;
        }
        System.out.println("Attributes for chunk: " + new String(chunk.getChunk_id(), StandardCharsets.UTF_8));
        for (Map.Entry<String, Object> set : attribs.entrySet()){
            if (set.getValue() instanceof int[]){
                System.out.println(set.getKey() + " -> " + Arrays.toString((int[]) set.getValue()));
            } else {
                System.out.println(set.getKey() + " -> " + set.getValue());
            }

        }
        // read pixel packing parameters from stored array
        int[] pixel_packing_params = (int[]) attribs.get("attributes_arrays");
        int first_packing_channel_count = pixel_packing_params[1];
        int second_packing_channel_count = pixel_packing_params[2];
        // skipping over package_type because its just the first two items in an array sortof
        int channel_count_sum = first_packing_channel_count + second_packing_channel_count;
        // dumb idea, check back later
        int bits = pixel_packing_params[8];
        // there are two asserts next, which we are replacing
        boolean packing_assert = (first_packing_channel_count == 1 && second_packing_channel_count == 4);
        boolean channel_assert = (channel_count_sum == 1);
        if (!(packing_assert || channel_assert)){
            System.out.println("packing type/channel count assert failed");
            return null;
        }
        int block_grid_width = (int) attribs.get("block_grid_width");
        int block_grid_height = (int) attribs.get("block_grid_height");
        if (!(block_grid_width * block_grid_height == chunk.getBitmap_blocks().size())){
            System.out.println("Bitmap grid assert failed");
            return null;
        }
        // read some more values
        int bitmap_width = (int) attribs.get("bitmap_width");
        int bitmap_height = (int) attribs.get("bitmap_height");
        // original python script says: >0 = white, else black
        int default_fill_black_white = (int) attribs.get("default_fill_black_white");
        // reading what sort of default fill information we need
        BufferedImage img;
        if (packing_assert){
            int[] default_fill;
            if (default_fill_black_white > 0){
                default_fill = new int[]{255, 255, 255, 255};
            } else {
                default_fill = new int[]{0, 0, 0, 0};
            }
            // create a new image
            img = new BufferedImage(bitmap_width, bitmap_height, BufferedImage.TYPE_4BYTE_ABGR);
            // we need to set its background based on our fill data we got
            Color fill = new Color(default_fill[0], default_fill[1], default_fill[2], default_fill[3]);
            Graphics2D g2d = img.createGraphics();
            g2d.setPaint(fill);
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
            g2d.dispose();
        } else {
            // check to make sure there is only 1 channel count; this is an assert in the original
            if (channel_count_sum != 1){
                System.out.println("Channel count sum assert failed, should be 1");
                return null;
            }
            int default_fill;
            if (default_fill_black_white > 0){
                default_fill = 255;
            } else {
                default_fill = 0;
            }
            // make our new buffered image
            img = new BufferedImage(bitmap_width, bitmap_height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2d = img.createGraphics();
            g2d.setPaint(new Color(default_fill, default_fill, default_fill));
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
            g2d.dispose();
        }
        // create graphics2d object
        Graphics2D g2d = img.createGraphics();
        // next we need to loop thu the bitmap blocks
        for (int y = 0; y < block_grid_height; y++){
            for (int x = 0; x < block_grid_width; x++){
                // get a bitmap block
                byte[] block = chunk.getBitmap_blocks().get((y * block_grid_width) + x);
                if (block != null){
                    // we need to decompress this shit
                    byte[] pixel_data = zlibUtils.zlibDecompressData(block);
                    if (pixel_data == null){
                        System.out.println("There was an error trying to decompress pixel data, skipping this block");
                        continue;
                    }
                    // this variable is defined for some reason
                    int k = 256 * 256;
                    // check packing type
                    BufferedImage drawn_block;
                    if (packing_assert){
                        // check length of data
                        if (pixel_data.length != (5 * k)){
                            System.out.println("Invalid pixel count for 4-channel block, expected 5*256*256, got " + pixel_data.length);
                            System.out.println("Skipping this block");
                            continue;
                        }
                        // the alpha is stored seperately, before the rest of the pixels
                        byte[] block_img_alpha = new byte[k];
                        System.arraycopy(pixel_data, 0, block_img_alpha, 0, k);
                        byte[] block_img_rgbx = new byte[5 * k];
                        int len = pixel_data.length - k;
                        System.arraycopy(pixel_data, k, block_img_rgbx, 0, len);
                        // now that we have the data, create the block we want
                        drawn_block = ImageBlockUtils.parseBitmapBlock(block_img_rgbx, block_img_alpha);
                    } else {
                        // TODO: something may be wrong with our grayscale handling, investigate
                        // sanity checks on the data first
                        if (pixel_data.length != k){
                            System.out.println("Invalid pixel count for 1-channel block, expected 256*256, got " + pixel_data.length);
                            System.out.println("Skipping this block");
                            continue;
                        }
                        // next, we need to copy our bytes to a new array
                        byte[] raw_grays = new byte[k];
                        System.arraycopy(pixel_data, 0, raw_grays, 0, k);
                        drawn_block = ImageBlockUtils.parseGrayscaleBitmapBlock(raw_grays);
                    }
                    // draw it to the main image
                    g2d.drawImage(drawn_block, null, x * 256, y * 256);
                    drawn_block.flush();
                }
            }
        }
        // dispose of graphics 2d
        g2d.dispose();
        // we're done, so return our completed image
        // note, we are inverting packing_assert, so true means false, etc.
        return new ChunkWithImage(chunk, img, !packing_assert, attribs);
    }

    /**
     * takes in raw list of ChunkWithImage, and loops thru it. this joins chunks together based on
     * if it detects monochrome chunks and chunks with the same layer names
     * @param chunks list of chunks to condense
     * @return a new list of chunks
     */
    public static ArrayList<ChunkWithImage> condenseChunks(ArrayList<ChunkWithImage> chunks){
        ArrayList<ChunkWithImage> newchunks = new ArrayList<>();
        ArrayList<ChunkWithImage> removedchunks = new ArrayList<>();
        // first, copy all non-momchrome chunks into the new chunks array
        for (ChunkWithImage chunkWithImage : chunks){
            if (!chunkWithImage.isMonochrome()) newchunks.add(chunkWithImage);
        }
        // funny variable for later
        int count = 0;
        for (ChunkWithImage chunkimage : chunks){
            // is this a monochrome chunk?
            if (chunkimage.isMonochrome()){
                // oh it is, find a chunk with a matching layer name
                ChunkWithImage base = null;
                for (ChunkWithImage cwi2 : newchunks){
                    if (cwi2.getLayer_name().equals(chunkimage.getLayer_name()) && !cwi2.isMonochrome()){
                        // found likely canidate. bail out of this for loop
                        base = cwi2;
                        break;
                    }
                }
                if (base != null){
                    // merge the monochrome chunk with the base chunk
                    base.add_mask(chunkimage.getImage());
                    // add ourselfs to the removed chunk list
                    removedchunks.add(chunkimage);
                    count++;
                    continue;
                } else {
                    System.out.println("Cannot find a non-monochrome chunk for " + chunkimage.getLayer_name() + ", skipping condensing");
                    newchunks.add(chunkimage);
                    continue;
                }
            }
        }
        System.out.println("Condensed " + count + " monochrome chunks into regular base chunks");
        return newchunks;
    }

}
