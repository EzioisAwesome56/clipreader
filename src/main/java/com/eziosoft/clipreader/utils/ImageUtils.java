package com.eziosoft.clipreader.utils;

import com.eziosoft.clipreader.objects.ChunkWithImage;
import com.eziosoft.clipreader.objects.LayerObject;
import org.jdesktop.swingx.graphics.BlendComposite;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ImageUtils {


    /**
     * called at the very end of the chain. used to reconstruct the image based on the order of the layers
     * provided
     * @param chunks chunks with their images made
     * @param layers list of sorted layers
     * @param width width of final image
     * @param height height of final image
     * @return completed image, or null if something breaks
     */
    public static BufferedImage reConstructImage(ArrayList<ChunkWithImage> chunks, ArrayList<LayerObject> layers, int width, int height){
        // create a new buffered image for drawing on
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        // create a graphics 2d instance from it
        Graphics2D g2d = img.createGraphics();
        // now, we can go thru all the layers, one after the other, in order and draw them to the final image
        for (int i = 0; i < layers.size(); i++){
            // get the current layer we are on
            LayerObject layer = layers.get(i);
            // get the current layerid
            int layer_id = layer.getLayer_id();
            // debugging shit
            System.out.println("Now drawing layer \"" + layer.getLayer_name() + "\"");
            // the script determins this using a logical AND with 1
            // true for visible, false for not visble
            boolean layer_vis = ((int) layer.getLayer_data().get("LayerVisibility") & 1) == 1;
            if (!layer_vis){
                // if its disabled, do not draw it
                System.out.println("Layer is not visible, skipping");
                continue;
            }
            // get the layerclip value; this will be important later
            int layer_clip = (int) layer.getLayer_data().get("LayerClip");
            // the database has layer x and y offsets, and the python script says you combine them like these
            // to get the proper layer offset
            int layer_x_offset = (int) layer.getLayer_data().get("LayerOffsetX") + (int) layer.getLayer_data().get("LayerRenderOffscrOffsetX");
            int layer_y_offset = (int) layer.getLayer_data().get("LayerOffsetY") + (int) layer.getLayer_data().get("LayerRenderOffscrOffsetY");
            // find the chunk with the matching id
            ChunkWithImage thechunk = null;
            for (ChunkWithImage c : chunks){
                if (c.getLayer_id() == layer_id){
                    // found the chunk we're looking for
                    thechunk = c;
                    break;
                }
            }
            // sanity check to make sure we have our image
            if (thechunk == null){
                System.out.println("ERROR: layerid "  + layer_id + " does not have an chunk tied to it. generation failed");
                return null;
            }
            // create a new list of buffered images
            ArrayList<BufferedImage> found_masks = new ArrayList<>();
            // does this chunk have any masks?
            if (!thechunk.getMasks().isEmpty()){
                for (int f = 0; f < thechunk.getMasks().size(); f++){
                    BufferedImage mask = thechunk.getMasks().get(f);
                    if (mask != null) found_masks.add(mask);
                }
            }
            // check if the clip value is greater than 0
            if (layer_clip > 0){
                // this means a previous layer has masks we need to obtain
                // we need to then find them
                for (int ii = 1; ii < layers.size(); ii++){
                    int new_index = i - ii;
                    // bounds checking
                    if (new_index < 0) break;
                    // get the layer at this new, previous ID
                    LayerObject obj = layers.get(new_index);
                    // check if clipping is enabled
                    if ((int) obj.getLayer_data().get("LayerClip") == 0){
                        System.out.println("Layer \"" + obj.getLayer_name() + "\" has clipping turned off, using that");
                        // we found the base layer, now we need to get its mask
                        ChunkWithImage h = null;
                        for (ChunkWithImage c : chunks){
                            if (c.getLayer_id() == obj.getLayer_id()){
                                // found what we're after
                                h = c;
                                break;
                            }
                        }
                        if (h != null){
                            // now we can add this layer's mask to our own list of masks
                            if (h.getMasks().size() > 0){
                                found_masks.addAll(h.getMasks());
                                // dont forget to break out of the main for loop
                                break;
                            }
                        }
                    } else {
                        // try and get its mask anyway
                        ArrayList<BufferedImage> temp = getMaskFromChunkIfAny(obj, chunks);
                        if (temp != null) found_masks.addAll(temp);
                    }
                }
            }
            // now we're done with that, do the processing required
            BufferedImage src;
            if (found_masks.size() > 0){
                // process all the image masks we found
                src = applyMasksToImage(thechunk.getImage(), found_masks, layer);
            } else {
                src = thechunk.getImage();
            }
            // get the composite mode for this layer
            g2d.setComposite(getCompositeMode((int) layer.getLayer_data().get("LayerComposite")));
            // because of SwingX, we need to convert it to a different format right before apply
            src = convertToIntARGB(src);
            // finally, we can draw it ontop of our canvas
            g2d.drawImage(src, null, 0 + layer_x_offset, 0 + layer_y_offset);
        }
        // TODO: the last bit of code left was ported from the original script. but i have yet to figure out what
        //      mipmaps are used for, so im leaving this here for now, but i doubt we actually need it
        /*for (LayerObject layer : layers){
            // check if the layer has a mask tied to it
            // FIXME: this code doesnt actually work, and i don't understand why
            if (layer.getRender_mipmap() != null){
                // find that chunk
                ChunkWithImage maskchunk = null;
                for (ChunkWithImage c : chunks){
                    if (c.getChunk_id().equals(layer.getRender_mipmap())){
                        // we found our chunk
                        maskchunk = c;
                        break;
                    }
                }
                if (maskchunk != null){
                    // TODO: do something with it?
                }
            }
        }*/
        // dispose of graphics 2d
        g2d.dispose();
        // return the finished image
        return img;
    }

    /**
     * gets masks from chunk for layer x, if it has any
     * @param layer layer to get mask for
     * @param chunks list of chunks
     * @return mask list, or null if nothing was found
     */
    private static ArrayList<BufferedImage> getMaskFromChunkIfAny(LayerObject layer, ArrayList<ChunkWithImage> chunks){
        // blank chunk object
        ChunkWithImage h = null;
        for (ChunkWithImage c : chunks){
            if (c.getLayer_id() == layer.getLayer_id()){
                // found what we're after
                h = c;
                break;
            }
        }
        // check to make sure the chunk isnt null
        if (h == null) return null;
        // try and see if it has any masks
        if (h.getMasks().isEmpty()) return null;
        // return the list of masks
        return h.getMasks();
    }

    /**
     * applies all masks to a layer, by doing dumb cringe math for each one that only kinda makes me wanna explode
     * hopefully this works
     * @param in image data to apply mask too
     * @param masks masks to apply
     * @return image with mask applied
     */
    private static BufferedImage applyMasksToImage(BufferedImage in, ArrayList<BufferedImage> masks, LayerObject layer){
        // loop thru all the masks and do the dumb stupid math
        for (BufferedImage mask : masks){
            // sanity check
            if (mask.getWidth() != in.getWidth() || mask.getHeight() != in.getHeight()){
                System.out.println("WARN: mask does not match dimensions of source image, Scaling");
                mask = scaleMaskToSize(mask, in.getWidth(), in.getHeight());
            }
            // we also need to do an inner loop for X and Y positions
            for (int y = 0; y < in.getHeight(); y++){
                for (int x = 0; x < in.getWidth(); x++){
                    // bounds checking sanity checks
                    Color mask_color = new Color(mask.getRGB(x, y));
                    // DO NOT FORGET TO TELL THIS THAT WE HAVE ALPHA!!!
                    Color src_color = new Color(in.getRGB(x, y), true);
                    // doesnt really matter what channel we pick, just pick one of them
                    int red = mask_color.getRed();
                    Color new_color;
                    if (red > 0){
                        // if its not black, do the math on that shit
                        new_color = new Color(src_color.getRed(), src_color.getBlue(), src_color.getGreen(), src_color.getAlpha());
                    } else {
                        // if it is black, delete the pixel entirely
                        new_color = new Color(0, 0, 0, 0);
                    }
                    // update the pixel in question
                    in.setRGB(x, y, new_color.getRGB());
                }
            }
            //System.out.println("pass1 done");
        }
        // we're done, return the source image
        return in;
    }

    /**
     * scales images to the provided width and height
     * @param in image to scale
     * @param width width to scale too
     * @param height height to scale too
     * @return scaled instance of image
     */
    private static BufferedImage scaleMaskToSize(BufferedImage in, int width, int height){
        // make a new buffered image
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        // scale the mask
        Image scaled = in.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        // use g2d to paint it onto img
        Graphics2D g2d = img.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return img;
    }

    /**
     * the swingx library does not work with 4byte abgr or any other weird formats, so we need to convert
     * to that format on the fly. this function does that
     * @param input buffered image to convert
     * @return converted buffered image
     */
    private static BufferedImage convertToIntARGB(BufferedImage input){
        // create a new buffered image of the right format
        BufferedImage buf = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        // get graphics 2d from this
        Graphics2D g2d = buf.createGraphics();
        // draw the image onto the thing
        g2d.drawImage(input, null, 0, 0);
        // dispose of graphics2d instance
        g2d.dispose();
        // return the converted image
        return buf;
    }

    /**
     * the python script has a table of these values, so i am just going to write some code based on that
     * because it will make things easier
     * @param mode mode selection value
     * @return a composition mode, the default g2d mode otherwise
     */
    private static Composite getCompositeMode(int mode){
        switch (mode){
            case 0:
                // normal
                return AlphaComposite.SrcOver;
            case 2:
                // multiply
                return BlendComposite.getInstance(BlendComposite.Multiply.getMode(), 0.0f);
            case 4:
                // photoshop says this one is "Linear Burn"
                // script calls it lbrn
                // FIXME: this probably doesnt line up exactly but its a guess?
                return BlendComposite.getInstance(BlendComposite.ColorBurn.getMode(), 0.0f);
            case 5:
                // subtract (or fsub in the list)
                return BlendComposite.getInstance(BlendComposite.Subtract.getMode(), 0.0f);
            case 10:
                // list says div, photoshop calls it "Color Dodge"
                return BlendComposite.getInstance(BlendComposite.ColorDodge.getMode(), 0.0f);
            case 12:
                // Linear Dodge, or lddg
                return BlendComposite.getInstance(BlendComposite.ColorDodge.getMode(), 0.0f);
            case 14:
                // overlay (over in the list)
                return BlendComposite.getInstance(BlendComposite.Overlay.getMode(), 0.0f);
            case 30:
                // Passthru mode
                // may apparently just be like alphacomp.srcover?
                return AlphaComposite.SrcOver;
            default:
                // Fallback
                System.out.println("Unknown composite mode: " + mode);
                return AlphaComposite.SrcOver;
        }
    }
}
