package com.eziosoft.clipreader;

import com.eziosoft.clipreader.objects.*;
import com.eziosoft.clipreader.utils.ImageUtils;
import com.eziosoft.clipreader.utils.chunkUtils;
import com.eziosoft.clipreader.utils.sqliteUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClipReader {
    // all of this is roughly based on https://github.com/dobrokot/clip_to_psd's clip2psd python script. the only tool I've found that
    // can actually read CSP files without needing CSP itself

    /**
     * this is the main function you call to get a preview of a clip file
     * ported from script "extract_csp" lines 2994 to 3052?
     * @param stream  stream of data from clip file you want to read
     * @return bufferedimage containing clip image data
     */
    public static BufferedImage readClipStream(InputStream stream) throws IOException{
        // do the basic init crap, since thats in its own function now
        BasicClipData basicClipData = readChunksandSQL(stream);
        SqliteData sqldata = basicClipData.getSqldata();
        ArrayList<FileChunk> fileChunks = basicClipData.getFileChunks();
        // next, we need to sort all of our layers
        // this function could be done better but oops i got lazy
        System.out.println("Detected layers:");
        ArrayList<LayerObject> sortedlayers = new ArrayList<>();
        printLayerFolders(sqldata.getRoot_folder(), 0, sqldata, sortedlayers);
        // now we have to convert various chunks into layers
        // or well the original code puts it into a dictionary
        LinkedHashMap<String, Integer> chunk_to_layers = new LinkedHashMap<String, Integer>();
        for (OffscreenChunksInfo oci : sqldata.getOffscreen()){
            chunk_to_layers.put(new String(oci.getBlockData(), StandardCharsets.UTF_8), oci.getLayerid());
        }
        // create a list of ids to layers
        HashMap<Integer, String> layer_names = new HashMap<Integer, String>();
        for (int i = 0; i < sqldata.getLayer().size(); i++){
            // get the main id
            int mainid = (int) sqldata.getLayer().get(i).get("MainId");
            // get the layer name
            String layername = (String) sqldata.getLayer().get(i).get("LayerName");
            // new entry
            layer_names.put(mainid, layername);
        }
        // extract the chunks
        ArrayList<Chunks> chunks = extractCSPChunkData(fileChunks, layer_names, chunk_to_layers);
        System.out.println("Number of chunks: " + chunks.size());
        // apply offscreen attributes to each chunk
        chunkUtils.applyOffscreenAttribsToChunks(chunks, sqldata);
        // next up: we will assign masks to layers
        sqliteUtils.assignMasksToLayers(sortedlayers, chunks, sqldata);
        // create new list to hold finished chunks
        ArrayList<ChunkWithImage> finished_chunks = new ArrayList<>();
        // then, run all the chunks thru the image decoder to make each one its own image
        for (Chunks chunk : chunks){
            // FIXME: the original script's test file is malformed and bad, so we have to work around it
            ChunkWithImage chunkWithImage = chunkUtils.decodeChunkToImage(chunk);
            if (chunkWithImage != null){
                // put it in the array
                finished_chunks.add(chunkWithImage);
            } else {
                System.out.println("WARN: decoding returned null chunk, skipping that invalid chunk");
            }
        }
        // list all chunks with names
        System.out.println("All chunks, in order:");
        for (ChunkWithImage chw : finished_chunks){
            System.out.println(chw.getChunk_id() + ": "  + chw.getLayer_name());
        }
        // condense chunks before creating final image
        finished_chunks =  chunkUtils.condenseChunks(finished_chunks);
        // create the final, reconstructed image
        BufferedImage finalimg = ImageUtils.reConstructImage(finished_chunks, sortedlayers, sqldata.getWidth(), sqldata.getHeight());
        // and then return it to the calling functionRebuildTest
        return finalimg;
    }

    /**
     * API shim to allow anything using the file method to still funtion
     * @param input file to read from
     * @return the rebuilt clip file from the stream
     * @throws IOException if something breaks horribly
     */
    public static BufferedImage readClipFile(File input) throws IOException {
        // create a stream from the file
        FileInputStream stream = new FileInputStream(input);
        // get what we want
        BufferedImage image = readClipStream(stream);
        // clean up
        stream.close();
        return image;
    }

    /**
     * API shim to allow file based methods to still  work with the new stream types
     * @param input input file to read from
     * @return object with csp preview information
     * @throws IOException if something broke horribly
     */
    public static CSPPreviewWithMeta readClipFilePreview(File input) throws IOException {
        // create a stream from the file
        FileInputStream stream = new FileInputStream(input);
        // get the data
        CSPPreviewWithMeta stuff = readClipStreamPreview(stream);
        // cleanup
        stream.close();
        return stuff;
    }

    /**
     * This does just the bare minimum to read the clip preview information from the .clip file
     * it does not do anything to reassemble the clip file from the layers
     * @param streamin image data to read in
     * @return bufferedimage of preview data
     * @throws IOException if something goes wrong
     */
    public static CSPPreviewWithMeta readClipStreamPreview(InputStream streamin) throws IOException{
        // do the basic init stuff
        BasicClipData basicClipData = readChunksandSQL(streamin);
        SqliteData sqldata = basicClipData.getSqldata();
        // check to make sure the preview data isnt null
        if (sqldata.getCanvas_preview() == null){
            throw new IOException("ERROR: no preview image data was found");
        } else {
            ImageIO.setUseCache(false);
            // get a stream and make imageio read it
            ByteArrayInputStream stream = new ByteArrayInputStream(sqldata.getCanvas_preview());
            BufferedImage buf = ImageIO.read(stream);
            // close the stream
            stream.close();
            // create an object to hold additional metadata
            CSPPreviewWithMeta meta = new CSPPreviewWithMeta(buf, sqldata.getHeight(), sqldata.getWidth());
            // return our meta object
            return meta;
        }
    }

    /**
     * API shim to allow file-based tests to work with the new
     * inputstream based methods
     * @param input file to process
     * @return arraylist of images of each layer
     * @throws IOException if something broke horribly
     */
    public static ArrayList<BufferedImage> readClipFileLayers(File input) throws IOException{
        // create a stream from the file
        FileInputStream stream = new FileInputStream(input);
        // run the main function
        ArrayList<BufferedImage> stuff = readClipStreamLayers(stream);
        // clean up
        stream.close();
        return stuff;
    }

    /**
     * this will read a .clip file and then return all layers that belong to it in an array
     * mostly a copy paste from the main file
     * @param stream inputstream of the file to read
     * @return array of images
     */
    public static ArrayList<BufferedImage> readClipStreamLayers(InputStream stream) throws IOException{
        // do the basic init crap, since thats in its own function now
        BasicClipData basicClipData = readChunksandSQL(stream);
        SqliteData sqldata = basicClipData.getSqldata();
        ArrayList<FileChunk> fileChunks = basicClipData.getFileChunks();
        System.out.println("Detected layers:");
        ArrayList<LayerObject> sortedlayers = new ArrayList<>();
        printLayerFolders(sqldata.getRoot_folder(), 0, sqldata, sortedlayers);
        // now we have to convert various chunks into layers
        // or well the original code puts it into a dictionary
        LinkedHashMap<String, Integer> chunk_to_layers = new LinkedHashMap<String, Integer>();
        for (OffscreenChunksInfo oci : sqldata.getOffscreen()){
            chunk_to_layers.put(new String(oci.getBlockData(), StandardCharsets.UTF_8), oci.getLayerid());
        }
        // create a list of ids to layers
        HashMap<Integer, String> layer_names = new HashMap<Integer, String>();
        for (int i = 0; i < sqldata.getLayer().size(); i++){
            // get the main id
            int mainid = (int) sqldata.getLayer().get(i).get("MainId");
            // get the layer name
            String layername = (String) sqldata.getLayer().get(i).get("LayerName");
            // new entry
            layer_names.put(mainid, layername);
        }
        // extract the chunks
        ArrayList<Chunks> chunks = extractCSPChunkData(fileChunks, layer_names, chunk_to_layers);
        // apply offscreen attributes to each chunk
        chunkUtils.applyOffscreenAttribsToChunks(chunks, sqldata);
        // create new list to hold finished chunks
        ArrayList<BufferedImage> imgs = new ArrayList<>();
        // then, run all the chunks thru the image decoder to make each one its own image
        for (Chunks chunk : chunks){
            imgs.add(chunkUtils.decodeChunkToImage(chunk).getImage());
        }
        // return the completed list
        return imgs;
    }

    /**
     * the copy and pasted basic init code from the two main functions was bothering me,
     * so i broke out the main parts between the two into this
     * this takes in a file and returns the sqlite data and a list of all file chunks
     * @param input file to read
     * @return metaobject containing the sqlite data and list of file chunks
     * @throws IOException if something explodes during the process
     */
    private static BasicClipData readChunksandSQL(InputStream input) throws IOException{
        // first we need to read in the entire file, for convience sake
        // the file has already read in before hand, so we can just patch around this
        byte[] sourcefile = IOUtils.toByteArray(input);
        // read in all the file chunks
        ArrayList<FileChunk> fileChunks = iterateFileChunks(sourcefile);
        // iterate thru all the chunks
        // create a tempfile for holding the sqlite database for later
        File temp_sqlite = File.createTempFile("clipreaderjvm", "sql");
        temp_sqlite.deleteOnExit();
        for (FileChunk chunk : fileChunks){
            if (chunk.getNameString().equals("SQLi")){
                System.out.println("Found SQLite Database chunk");
                // write to a file
                FileUtils.writeByteArrayToFile(temp_sqlite, chunk.getData());
            }
        }
        // next we need to get the sqlite data
        SqliteData sqldata;
        try {
            sqldata = getSqlDataLayerChunks(temp_sqlite);
        } catch (Exception e){
            System.out.println("error during SQLITE phase");
            throw new IOException("Error while trying to do SQLIte things", e);
        }
        // delete temp file NOW actually
        temp_sqlite.delete();
        // make a new object to hold all the data we just got
        BasicClipData basic = new BasicClipData(sqldata, fileChunks);
        // return that metaobject
        return basic;
    }

    /**
     * detects if file is valid by looking for the file header.
     * this usually only requires the first 24 or so bytes of the file, but
     * some more may be required
     * @param data read in bytes from the source file
     * @return true if file is valid; false if it is not valid
     */
    public static boolean isProbablyValidFile(byte[] data){
        // wrap entire thing in try catch loop
        try {
            /* this code is copied from the main iterateFileChunks function,
            just it checks for file header and the first chunk header */
            // hardcoded constant values from the original script, line 93
            int file_header_size = 24;
            // get the header from the data?
            byte[] file_header = new byte[file_header_size];
            System.arraycopy(data, 0, file_header, 0, file_header_size);
            // make sure its a header string, we only need to check the first 8 bytes
            String temp = new String(file_header, StandardCharsets.UTF_8).substring(0, 8);
            if (!temp.equals("CSFCHUNK")){
                throw new IOException("ERROR: read in file does not have the right chunk header!");
            }
            // try to verify the first chunk; should be 4 byte long "CHNK" string
            byte[] tempbyte = new byte[4];
            System.arraycopy(data, file_header_size, tempbyte, 0, 4);
            temp = new String(tempbyte, StandardCharsets.UTF_8);
            if (!temp.equals("CHNK")){
                throw new IOException("ERROR: failed to find first chunk after header!");
            }
            // if we have gotten here without failing any checks, the file is probably valid
            return true;
        } catch (Exception e){
            // fail if any errors are encountered
            System.err.println("Error while trying to read file:");
            e.printStackTrace();
        }
        return false;
    }


    /**
     * port of the python script's "iterate_file_chunks" function
     * lines 92 to 121
     * @param data byte array of the csp file we read in
     * @return list of chunks
     */
    private static ArrayList<FileChunk> iterateFileChunks(byte[] data) throws IOException{
        // temp variable because i fucking hate math
        int fuck;
        // hardcoded constant values from the original script, line 93
        int file_header_size = 24;
        // get the header from the data?
        byte[] file_header = new byte[file_header_size];
        System.arraycopy(data, 0, file_header, 0, file_header_size);
        // make sure its a header string, we only need to check the first 8 bytes
        String temp = new String(file_header, StandardCharsets.UTF_8).substring(0, 8);
        if (!temp.equals("CSFCHUNK")){
            throw new IOException("ERROR: read in file does not have the right chunk header!");
        }
        // try to verify the first chunk; should be 4 byte long "CHNK" string
        byte[] tempbyte = new byte[4];
        System.arraycopy(data, file_header_size, tempbyte, 0, 4);
        temp = new String(tempbyte, StandardCharsets.UTF_8);
        if (!temp.equals("CHNK")){
            throw new IOException("ERROR: failed to find first chunk after header!");
        }
        // make a array of new chunks
        ArrayList<FileChunk> chunks = new ArrayList<>();
        // setup some counters
        int chunk_offset = file_header_size;
        while (chunk_offset < data.length){
            // make new tempbyte array, needs to be 4 bytes long
            tempbyte = new byte[4];
            // copy data into it
            System.arraycopy(data, chunk_offset, tempbyte, 0, 4);
            // check to see if chunk header
            temp = new String(tempbyte, StandardCharsets.UTF_8);
            if (!temp.equals("CHNK")){
                throw new IOException("Error: failed to find next chunk after header");
            }
            // do some unknown magic
            // the script heavily uses python : operator, which is essentially
            // "only show from start index to end index", or s:e
            fuck = (chunk_offset + 4 * 4) - chunk_offset;
            byte[] chunk_header = new byte[fuck];
            System.arraycopy(data, chunk_offset, chunk_header, 0, fuck);
            fuck = (8 - 4);
            byte[] chunk_name = new byte[fuck];
            System.arraycopy(chunk_header, 4, chunk_name, 0, fuck);
            fuck = (12 - 8);
            byte[] zero1 = new byte[fuck];
            System.arraycopy(chunk_header, 8, zero1, 0, fuck);
            fuck = (16 - 12);
            byte[] size_bin = new byte[fuck];
            System.arraycopy(chunk_header, 12, size_bin, 0, fuck);
            // we need to check if zero1 is 4 nulls or not
            int num_nulls = 0;
            for (byte b : zero1){
                if (b == 0x0) num_nulls++;
            }
            if (num_nulls < 4) System.out.println("Warn: number of nulls is not atleast 4");
            // convert the size to an int, big endian ordering
            ByteBuffer bytebuffer = ByteBuffer.wrap(size_bin);
            bytebuffer.order(ByteOrder.BIG_ENDIAN);
            int chunk_data_size = bytebuffer.getInt();
            // next we have to get the chunk data itself
            byte[] chunk_data = new byte[chunk_data_size];
            System.arraycopy(data, chunk_offset + 16, chunk_data, 0, chunk_data_size);
            // put it in our chunk list
            chunks.add(new FileChunk(chunk_name, chunk_data, chunk_offset));
            // increment the chunk offset
            chunk_offset += (16 + chunk_data_size);
        }
        // we're done, return the chunk list array
        return chunks;
    }

    /**
     * port of "get_sql_data_layer_chunks" lines 271-323
     * @param database file generated eariler of the sqlite database
     */
    private static SqliteData getSqlDataLayerChunks(File database) throws SQLException {
        // setup the sqlite connection driver
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
        Statement statement = conn.createStatement();
        // some hard-coded query strings come next
        String query_offscreen_chunks = "SELECT MainId, LayerId, BlockData, Attribute from Offscreen;";
        String query_layer = "SELECT * FROM Layer;";
        String query_mipmap = "SELECT MainId, BaseMipmapInfo from Mipmap";
        String query_mipmap_info = "SELECT MainId, Offscreen from MipmapInfo";
        String query_vector_chunks = "SELECT MainId, VectorData, LayerId from VectorObjectList";
        // get the table columns
        // ...i dont know if i even need this. we may ignore it for now
        HashMap<String, String[]> table_colums = sqliteUtils.get_database_columns(statement);
        // next we need to get offscreen chunsk into
        ArrayList<OffscreenChunksInfo> offscreen_chunks_sqlite_info = new ArrayList<>();
        // execute the query to get this information
        ResultSet rs = statement.executeQuery(query_offscreen_chunks);
        while (rs.next()){
            OffscreenChunksInfo info = new OffscreenChunksInfo(rs.getInt("MainId"), rs.getInt("LayerId"),
                    rs.getBytes("BlockData"), rs.getBytes("Attribute"));
            offscreen_chunks_sqlite_info.add(info);
        }
        // next up is layer information
        ArrayList<HashMap<String, Object>> layer_sqlite_info = new ArrayList<>();
        rs = statement.executeQuery(query_layer);
        // check to make sure this table exists in our array from eariler
        if (!table_colums.containsKey("Layer")){
            throw new SQLException("ERROR: list of tables is missing the required Layer table!");
        }
        int num_cols = table_colums.get("Layer").length;
        while (rs.next()){
            // make a new temp hashmap
            HashMap<String, Object> temp = new HashMap<>();
            // populate it
            for (int i = 0; i < num_cols; i++){
                temp.put(table_colums.get("Layer")[i], rs.getObject(table_colums.get("Layer")[i]));
            }
            // append to our global array
            layer_sqlite_info.add(temp);
        }
        // we should also get the mipmap information; we may need this later
        ArrayList<MipmapObject> mipmap_sqlite_info = new ArrayList<>();
        rs = statement.executeQuery(query_mipmap);
        while (rs.next()){
            MipmapObject mipmap = new MipmapObject(rs.getInt("MainId"), rs.getInt("BaseMipmapInfo"));
            mipmap_sqlite_info.add(mipmap);
        }
        // then, do the same thing again for the mipmapinfo table
        ArrayList<MipmapInfoObject> mipmapinfo_sqlite_info = new ArrayList<>();
        rs = statement.executeQuery(query_mipmap_info);
        while (rs.next()){
            MipmapInfoObject mipinfo = new MipmapInfoObject(rs.getInt("MainId"), rs.getInt("Offscreen"));
            mipmapinfo_sqlite_info.add(mipinfo);
        }

        // TODO: we may not need the rest of the data here, we're skipping to the end of the data now
        // we need to get the width, hieght and dpi of the file
        rs = statement.executeQuery("SELECT CanvasWidth, CanvasHeight, CanvasResolution from Canvas");
        int witdh = rs.getInt("CanvasWidth");
        int height = rs.getInt("CanvasHeight");
        int dpi = rs.getInt("CanvasResolution");
        // we also need to get the root folder
        rs = statement.executeQuery("SELECT CanvasRootFolder FROM Canvas");
        int root = rs.getInt("CanvasRootFolder");
        // FIXME: actually we don't need like, most of the data, there is a table called "CanvasPreview" that has exactly what we want
        //      most of this code will go completely unused and thats extremely funny to me
        rs = statement.executeQuery("SELECT ImageData FROM CanvasPreview");
        byte[] prevdata = rs.getBytes("ImageData");
        // create object
        SqliteData data = new SqliteData(offscreen_chunks_sqlite_info, layer_sqlite_info, witdh, height, dpi, prevdata, root, mipmap_sqlite_info, mipmapinfo_sqlite_info);
        // close everything
        statement.close();
        return data;
    }

    // constants that always need to be present
    public static String bdbc_string = "BlockDataBeginChunk";
    public static byte[] BlockDataBeginChunk = bdbc_string.getBytes(StandardCharsets.UTF_16BE);
    public static String bs_string = "BlockStatus";
    public static byte[] BlockStatus = bs_string.getBytes(StandardCharsets.UTF_16BE);
    public static String bcs_string = "BlockCheckSum";
    public static byte[] BlockCheckSum = bcs_string.getBytes(StandardCharsets.UTF_16BE);
    public static String bdec_string = "BlockDataEndChunk";
    public static byte[] BlockDataEndChunk = bdec_string.getBytes(StandardCharsets.UTF_16BE);

    /**
     * port of "extract_csp_chunks_data" from the original python script
     * this probably won't get used for most use cases, but will maybe be useful in reconstructing the original image
     * if a preview doesnt exist
     * @param filechunks chunks from the file parsed originally
     */
    private static ArrayList<Chunks> extractCSPChunkData(ArrayList<FileChunk> filechunks, HashMap<Integer, String> names, HashMap<String, Integer> chunk2layers){
        // temp variables
        byte[] bytetemp;
        ByteBuffer tempbuf;
        String tempstring;
        String tempstring2;
        // first we need to make an empty array of shit to return later
        ArrayList<Chunks> chunks = new ArrayList<>();
        // loop thru the file chunks list
        for (FileChunk fc : filechunks){
            int chunk_data_size = fc.getData().length;
            // check the name of the chunk
            if (fc.getNameString().equals("Exta")){
                // we need to figure out the length of its name
                bytetemp = new byte[8];
                System.arraycopy(fc.getData(), 0, bytetemp, 0, 8);
                tempbuf = ByteBuffer.wrap(bytetemp);
                tempbuf.order(ByteOrder.BIG_ENDIAN);
                int chunk_name_length = Math.toIntExact(tempbuf.getLong());
                // close it
                tempbuf.clear();
                // sanity check i think?
                if (chunk_name_length != 40) System.out.println("WARN: unusual chunk name length, should be 40 but instead is " + chunk_name_length);
                // next, get the chunk id
                byte[] chunk_id = new byte[chunk_name_length];
                System.arraycopy(fc.getData(), 8, chunk_id, 0, chunk_name_length);
                tempstring = new String(chunk_id, StandardCharsets.UTF_8);
                // more sanity checks
                if (!tempstring.startsWith("extrnlid")) System.out.println("WARN: unusual chunk name, should start with 'extrnlid', instead is " + tempstring);
                // debugging crap
                System.out.println("Chunk name: " + fc.getNameString() + ", chunk data size: " + chunk_data_size + ", offset: " + fc.getOffset() + ", id: " + new String(chunk_id, StandardCharsets.UTF_8));
                // we need to get something called chunksize2
                bytetemp = new byte[8];
                System.arraycopy(fc.getData(), chunk_name_length + 8, bytetemp, 0, 8);
                // convert to int
                tempbuf = ByteBuffer.wrap(bytetemp);
                tempbuf.order(ByteOrder.BIG_ENDIAN);
                int chunk_size2 = Math.toIntExact(tempbuf.getLong());
                tempbuf.clear();
                // sanity checks
                if (chunk_data_size != (chunk_size2 + 16 + chunk_name_length)) System.out.println("Warning: unusual second chunk size value");
                // next, we need to get binary data for this chunk
                byte[] chunk_binary_data = new byte[fc.getData().length - (chunk_name_length + 8 + 8)];
                System.arraycopy(fc.getData(), chunk_name_length + 8 + 8, chunk_binary_data, 0, fc.getData().length - (chunk_name_length + 8 + 8));
                // do some bit shuffling to make sure we don't load then we need to atm
                bytetemp = new byte[BlockDataBeginChunk.length];
                System.arraycopy(chunk_binary_data, 8, bytetemp, 0, BlockDataBeginChunk.length);
                // convert that to a string
                tempstring = new String(bytetemp, StandardCharsets.UTF_16BE);
                String ext;
                // create this list for bitmap blocks
                ArrayList<byte[]> bitmap_blocks = null;
                if (tempstring.equals(bdbc_string)){
                    // see if we get anything
                    bitmap_blocks = chunkUtils.parseChunkWithBlocks(chunk_binary_data);
                    if (bitmap_blocks == null){
                        // bad block probably
                        System.out.println("ERROR: cant parse bitmap block " + chunk_id + " at " + fc.getOffset());
                        continue;
                    }

                    // otherwise, assume we passed and assign it to be png
                    ext = "png";
                } else {
                    ext = "bin";
                }
                // next we have to do some pattern matching
                Pattern pattern = Pattern.compile("^[a-zA-Z0-9_-]+$");
                // BUT WAIT! we need to convert this shit to a string
                tempstring2 = new String(chunk_id, StandardCharsets.UTF_8);
                Matcher matcher = pattern.matcher(tempstring2);
                if (!matcher.find()) System.out.println("WARN: Unusual chunk ID: " + tempstring2);
                // attempt to get the chunk's layer id
                // FIXME: the test file from the original repo throws a nullpointer here, so catch that
                int chunk_layerid;
                try {
                   chunk_layerid = chunk2layers.get(tempstring2);
                   tempstring = names.get(chunk_layerid);
                } catch (NullPointerException e){
                    // error, ignore it and assign fallback
                    chunk_layerid = -1;
                    tempstring = "";
                }
                // then also get the layer's name
                if (tempstring.isEmpty()){
                    // just use a placeholder name, the original script also does this more or less
                    tempstring = "{no name}";
                }
                System.out.println("This chunk belongs to layer id " + chunk_layerid + " which is named: " + tempstring);
                // TODO: the original script does some extra stuff with the other data
                //      i dont really care, i just want the bitmap information, so we can just skip this shit
                // TODO: ^ investigate this, i think i just implimented all it does to pair chunks to layers
                // make a new chunk
                Chunks achunk = new Chunks(tempstring, "filename", bitmap_blocks, chunk_layerid, chunk_id);
                // add it to our list of chunks
                chunks.add(achunk);
            } else {
                // TODO: should we care? i dont think we should
                System.out.println("Weird chunk that we probaly don't care about");
            }
        }
        // in theory we're done, so return our chunks
        return chunks;
    }

    /**
     * rough port of "print_layer_folders" from the script
     * this is used to sort the layers in order on the list
     * we will probably be ignoring folder information, because folders arent helpful to what
     * we're trying to do
     * @param folder_id
     * @param depth
     * @param data all of the data we extracted from the SQLite database eariler
     */
    private static void printLayerFolders(int folder_id, int depth, SqliteData data, ArrayList<LayerObject> ordered){
        // get the root folder
        HashMap<String, Object> folder = null;
        // fine the object with the mainid of 2
        for (int i = 0; i < data.getLayer().size(); i++){
            if ((int) data.getLayer().get(i).get("MainId") == folder_id){
                System.out.println("Found Layer " + folder_id);
                folder = data.getLayer().get(i);
                break;
            }
        }
        if (folder == null){
            System.out.println("Error: no root layer was found. exiting");
            return;
        }
        int current_id = (int) folder.get("LayerFirstChildIndex");
        // python uses >0 = true, so we shall assume it is also 0
        while (current_id > 0){
            // get the layer by finding it by its id
            HashMap<String, Object> layer = null;
            for (int i = 0; i < data.getLayer().size(); i++){
                HashMap<String, Object> layerinfo = data.getLayer().get(i);
                if ((int) layerinfo.get("MainId") == current_id){
                    // found it
                    layer = layerinfo;
                    break;
                }
            }
            if (layer == null){
                System.out.println("Failed to find layer with ID: " + current_id);
                return;
            }
            // make new holding object for it
            LayerObject thelayer;
            // check if this is a subfolder or not
            boolean is_subfolder = ((int) layer.get("LayerFolder") != 0);
            if (is_subfolder){
                thelayer = new LayerObject(true, layer, current_id, (String) layer.get("LayerName"));

            } else {
                thelayer = new LayerObject(false, layer, current_id, (String) layer.get("LayerName"));
            }
            // add this layer to our list
            ordered.add(thelayer);
            if (thelayer.isIs_folder()){
                // RECURSIVE OH NO
                printLayerFolders(current_id, depth + 1, data, ordered);
            }
            // get the next id
            current_id = (int) layer.get("LayerNextIndex");
        }
        // done sorting, display our layer information
        // dumb stupid hack to make sure we aren't running this recursively
        if (depth == 0) {
            for (LayerObject layer : ordered) {
                StringBuilder b = new StringBuilder();
                b.append("Layer ID: ").append(layer.getLayer_id()).append(", Layer Name: ").append(layer.getLayer_name()).append(", is folder: ").append(layer.isIs_folder());
                System.out.println(b.toString());
            }
        }
    }

    private static void debug_WriteBytes(byte[] in){
        try {
            System.out.write(in);
            System.out.println();
        } catch (IOException e){
            System.out.println("failed to write bytes");
        }
    }
}
