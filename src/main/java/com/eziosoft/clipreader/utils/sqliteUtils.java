package com.eziosoft.clipreader.utils;

import com.eziosoft.clipreader.objects.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class sqliteUtils {

    /**
     * port of "get_database_columns" lines 260-269
     * also based on printed output of what it returns
     * @param statement
     */
    public static HashMap<String, String[]> get_database_columns(Statement statement) throws SQLException {
        // create a temp place to hold strings
        ArrayList<String> tablenames = new ArrayList<>();
        // execute the query
        ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_schema WHERE type == 'table' ORDER BY name");
        while (rs.next()){
            // put the table name in the array
            tablenames.add(rs.getString("name"));
        }
        System.out.println("Tables obtained: " + tablenames.size());
        // create output map
        HashMap<String, String[]> output = new HashMap<>();
        // loop thru all the tables
        for (String s : tablenames){
            // the original matches a regex expression so
            Pattern pattern = Pattern.compile("^[a-zA-Z0-9_.]+$");
            Matcher match = pattern.matcher(s);
            if (!match.find()){
                // there was no match, go to the next item
                System.out.println("Regex did not find a match");
                continue;
            }
            // insert a new entry into the map
            output.put(s, getTableColumns(statement, s));
        }
        return output;
    }

    private static String[] getTableColumns(Statement state, String table) throws SQLException {
        ArrayList<String> temp = new ArrayList<>();
        // make the query
        ResultSet rs = state.executeQuery("SELECT name FROM pragma_table_info('" + table + "')  WHERE type <> '' order by name");
        while (rs.next()){
            temp.add(rs.getString("name"));
        }
        // convert that to a string
        return temp.toArray(temp.toArray(new String[0]));
    }

    /**
     * rough port of "parse_offscreen_attributes_sql_value" from the script
     * @param offscreen byte array that needs parsing
     */
    public static HashMap<String, Object> parseOffscreenAttributesSqlValue(byte[] offscreen){
        // create our output object
        HashMap<String, Object> parsed = new HashMap<>();
        // convert to an input stream?
        // FIXME: make sure this shit isnt null
        if (offscreen == null){
            System.out.println("ERROR: empty byte array while trying to parse offscreen data");
            return null;
        }
        ByteArrayInputStream stream = new ByteArrayInputStream(offscreen);
        try {
            // get header size
            int header_size = readInt(stream);
            // the script uses an assert to check this
            if (header_size != 16){
                System.out.println("ERROR: header size is not 16!");
                return null;
            }
            // do this again but for the second size
            int info_section_size = readInt(stream);
            if (info_section_size != 102){
                System.out.println("ERROR: info_section_size is not 102!");
                return null;
            }
            // one more time!
            int extra_info_section_size = readInt(stream);
            if (!(extra_info_section_size == 42 || extra_info_section_size == 58)){
                System.out.println("ERROR: extra_info_section_size is not a valid value! its " + extra_info_section_size);
                return null;
            }
            // we apparently just skip the next int's worth of data
            readInt(stream);
            // check to see if we have a match for a hard-coded string
            if (!checkReadStr("Parameter", stream)){
                System.out.println("Parameter assertion failed");
                return null;
            }
            // read a bunch of ints next
            parsed.put("bitmap_width", readInt(stream));
            parsed.put("bitmap_height", readInt(stream));
            parsed.put("block_grid_width", readInt(stream));
            parsed.put("block_grid_height", readInt(stream));
            // now, we have to read a lot of ints. the script calls for atleast 16 of them
            int[] attributes_arrays = new int[16];
            for (int i = 0; i < attributes_arrays.length; i++){
                attributes_arrays[i] = readInt(stream);
            }
            // FIXME: this is refered to later as "pixel_packing_params" in the script...so maybe change it?
            parsed.put("attributes_arrays", attributes_arrays);
            // check another string
            if (!checkReadStr("InitColor", stream)){
                System.out.println("ERROR: InitColor assert failed");
                return null;
            }
            // throw out an int
            readInt(stream);
            // this int actually matters
            parsed.put("default_fill_black_white", readInt(stream));
            // throw out 3 more ints
            readInt(stream);
            readInt(stream);
            readInt(stream);
            // create a blank array with 0s in it
            int[] init_color = new int[]{0, 0, 0, 0};
            // magic number that i have no idea what it means
            // the original script had it like this
            if (extra_info_section_size == 58){
                for (int i = 0; i < init_color.length; i++){
                    // replacement for python's min()
                    ArrayList<Integer> temp = new ArrayList<>();
                    temp.add(255);
                    temp.add(Math.floorDiv(readInt(stream), (int) Math.pow((double) 256, (double) 3)));
                    init_color[i] = Collections.min(temp);
                }
            }
            // add this final object to our output object
            parsed.put("init_color", init_color);
            // return our list
            return parsed;
        } catch (IOException e){
            System.out.println("Error while trying to parse offscreen attributes");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * this function was inlined originally
     * @param source
     * @param stream
     * @return
     */
    private static boolean checkReadStr(String source, InputStream stream) throws IOException{
        // go read in a string
        String read = readCSPUnicodeStr(stream);
        // assert was used in python originally, but i dont thing those work in non-debug enviroments
        return read.equals(source);

    }

    private static int readInt(InputStream stream) throws IOException {
        // do some really dumb shit to read 4 bytes out of the stream
        byte[] temp = new byte[4];
        stream.read(temp, 0, 4);
        // then we have to convert to an int
        ByteBuffer buf = ByteBuffer.wrap(temp);
        // needs to be big endian order
        buf.order(ByteOrder.BIG_ENDIAN);
        int res = buf.getInt();
        // cleanup and return
        buf.clear();
        return res;
    }

    /**
     * rough port of "read_csp_unicode_str"
     * @param stream input stream to read from
     * @return string, or null if size is -1
     */
    private static String readCSPUnicodeStr(InputStream stream) throws IOException{
        // read the thing
        int str_size = readIntMaybe(stream);
        if (str_size == -1) return null;
        // now we have to read a lot of data from the thing
        // script says its UTF-16BE so 2 bytes per char
        int real_stringsize = (2 * str_size);
        byte[] read_data = new byte[real_stringsize];
        // TODO: maybe add a sanity check to make sure this isnt -1?
        stream.read(read_data, 0, real_stringsize);
        return new String(read_data, StandardCharsets.UTF_16BE);
    }

    /**
     * extremely funny this function is called this, but it looks like its trying to read,
     * but if it reads less then 4, bail out
     * @param stream stream to read from
     * @return int, or -1 if condition fails
     */
    private static int readIntMaybe(InputStream stream) throws IOException{
        // make a list for temp storage shit
        ArrayList<Byte> storage = new ArrayList<Byte>();
        while (true){
            // read value
            int value = stream.read();
            // this call will return -1 if fail, so just break out if that happens
            if (value == -1) break;
            storage.add((byte) value);
            // we need a way to like, actually exit the loop lmao
            if (storage.size() == 4) break;
        }
        // check to make sure we read 4 bytes
        if (storage.size() != 4){
            return -1;
        }
        // otherwise, get an int and return it
        // TODO: this code is dumb but java was having a hissy fit
        byte[] bruh = new byte[4];
        for (int i = 0; i < 4; i++){
            bruh[i] = storage.get(i);
        }
        ByteBuffer buf = ByteBuffer.wrap(bruh);
        buf.order(ByteOrder.BIG_ENDIAN);
        int res = buf.getInt();
        // cleanup
        buf.clear();
        return res;
    }

    /**
     * really rough port of "get_layer_bitmaps" from the script
     * should hopefully assign masks to layers
     * @param layers layers to assign masks too
     * @param chunks chunks to scan for masks
     */
    public static void assignMasksToLayers(ArrayList<LayerObject> layers, ArrayList<Chunks> chunks, SqliteData sql){
        // do a loop thru every later pretty much
        for (LayerObject layer : layers){
            Chunks render = null;
            Chunks mask = null;
            // get the layer render mipmap id first
            int render_id = (int) layer.getLayer_data().get("LayerRenderMipmap");
            if (render_id > 0){
                // do the INSANELY stupid id lookup in the offscreen data array
                int mipmap_baseinfo = -1;
                for (MipmapObject map : sql.getMipmap()){
                    if (map.getMainid() == render_id){
                        mipmap_baseinfo = map.getBasemipmapinfo();
                        break;
                    }
                }
                if (mipmap_baseinfo == -1){
                    // we failed, skip this
                    System.out.println("Unable to find mipmap object to match, skipping");
                    // TODO: dunno what to do here. maybe nothing?
                } else {
                    // then, lookup the same shit in the offscreen table
                    OffscreenChunksInfo offinfo = null;
                    for (MipmapInfoObject infoobj : sql.getMipmapinfo()){
                        if (infoobj.getMainid() == mipmap_baseinfo){
                            // found it, get the offscreen information now
                            for (OffscreenChunksInfo oofoof : sql.getOffscreen()){
                                if (oofoof.getMainid() == infoobj.getOffscreen()){
                                    // found what we're after
                                    offinfo = oofoof;
                                    break;
                                }
                            }
                            if (offinfo != null) break;
                        }
                    }
                    // make sure its not null, if it is skip if
                    if (offinfo == null){
                        System.out.println("Failed to find offscreen info for this, doing nothing");
                    } else {
                        // ok, now we have access to block data
                        // convert to string
                        String extern_id = new String(offinfo.getBlockData(), StandardCharsets.UTF_8);
                        // look to see if we have it in a chunk
                        for (int i = 0; i < chunks.size(); i++){
                            // get the chunk
                            Chunks ch = chunks.get(i);
                            String temp = new String(ch.getChunk_id(), StandardCharsets.UTF_8);
                            // check if it matches
                            if (extern_id.equals(temp)){
                                // found the matching junk, yoink its block data
                                render = ch;
                                // delete the chunk from the list
                                //chunks.remove(i);
                                // yeet
                                break;
                            }
                        }
                    }
                }
            }
            // now we do that again for the mask mipmap
            // yiy will have to excuse me for the blanet copy paste and edit im lazy
            int mask_id = (int) layer.getLayer_data().get("LayerLayerMaskMipmap");
            if (mask_id > 0){
                // do the INSANELY stupid id lookup in the offscreen data array
                int mipmap_baseinfo = -1;
                for (MipmapObject map : sql.getMipmap()){
                    if (map.getMainid() == render_id){
                        mipmap_baseinfo = map.getBasemipmapinfo();
                        break;
                    }
                }
                if (mipmap_baseinfo == -1){
                    // we failed, skip this
                    System.out.println("Unable to find mipmap object to match, skipping");
                    // TODO: dunno what to do here. maybe nothing?
                } else {
                    // then, lookup the same shit in the offscreen table
                    OffscreenChunksInfo offinfo = null;
                    for (MipmapInfoObject infoobj : sql.getMipmapinfo()){
                        if (infoobj.getMainid() == mipmap_baseinfo){
                            // found it, get the offscreen information now
                            for (OffscreenChunksInfo oofoof : sql.getOffscreen()){
                                if (oofoof.getMainid() == infoobj.getOffscreen()){
                                    // found what we're after
                                    offinfo = oofoof;
                                    break;
                                }
                            }
                            if (offinfo != null) break;
                        }
                    }
                    // make sure its not null, if it is skip if
                    if (offinfo == null){
                        System.out.println("Failed to find offscreen info for this, doing nothing");
                    } else {
                        // ok, now we have access to block data
                        // convert to string
                        String extern_id = new String(offinfo.getBlockData(), StandardCharsets.UTF_8);
                        // look to see if we have it in a chunk
                        for (int i = 0; i < chunks.size(); i++){
                            // get the chunk
                            Chunks ch = chunks.get(i);
                            String temp = new String(ch.getChunk_id(), StandardCharsets.UTF_8);
                            // check if it matches
                            if (extern_id.equals(temp)){
                                // found the matching junk, yoink its block data
                                mask = ch;
                                // delete the chunk from the list
                                //chunks.remove(i);
                                // yeet
                                break;
                            }
                        }
                    }
                }
            }
            String temp1 = null;
            String temp2 = null;
            // ok, now we have the shit, so we can do things with it
            if (render != null){
                temp1 = new String(render.getChunk_id(), StandardCharsets.UTF_8);
                layer.setRender_mipmap(temp1);
            }
            if (mask != null){
                temp2 = new String(mask.getChunk_id(), StandardCharsets.UTF_8);
                layer.setMaskmipmap(temp2);
            }
            // out of sheer curosity...
            if (temp1 != null && temp2 != null){
                if (temp2.equals(temp1)) System.out.println("Render and Mask chunks are identical!");
            }
        }
    }
}
