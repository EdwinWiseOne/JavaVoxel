package com.simreal.VoxEngine.brick;

import com.simreal.VoxEngine.Material;
import com.simreal.VoxEngine.Texture;
import com.simreal.VoxEngine.VoxTree;
import org.apache.hadoop.hbase.util.Bytes;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import javax.vecmath.Point3i;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * The Brick Factory generates texture brick bases parametrically, and then allows
 * them to be viewed and edited interactively.
 *
 * The Brick Factory is tied into the UserInput to process Control-Key behavior
 * for selecting base bricks and adjusting the generator parameters.
 *
 * USE INPUT SCHEMA:
 *
 * Ctrl-Q           Cycle quantization value forwards
 * Ctrl-Shift-Q     Cycle quantization value backwards
 *
 * Ctrl-B           Cycle the band clamp value forwards
 * Ctrl-Shift-B     Cycle the band clamp value backwards
 *
 * Ctrl-S           Cycle the scaling value forwards
 * Ctrl-Shift-S     Cycle the scaling value backwards
 *
 * Ctrl-D           Cycle the curl decay value forwards
 * Ctrl-Shift-D     Cycle the curl decay value backwards
 *
 * Ctrl-R           Toggle the reflection flag
 * Ctrl-Shift-R     Toggle the square flag
 * Ctrl-I           Toggle the inversion flag
 *
 * TODO: a better method of selecting bricks, FAR MORE bricks
 * TODO: Make better textures and colors; these are for testing
 * Ctrl-0           Select brick pattern 0, test
 * Ctrl-1           Select brick pattern 1, coal
 * Ctrl-2           Select brick pattern 2, stone
 * Ctrl-3           Select brick pattern 3, dirt
 * Ctrl-4           Select brick pattern 4, steel
 * Ctrl-5           Select brick pattern 5
 * Ctrl-6           Select brick pattern 6
 * Ctrl-7           Select brick pattern 7
 * Ctrl-8           Select brick pattern 8
 * Ctrl-9           Select brick pattern 9
 * Ctrl-0           Select brick pattern 0
 *
 */
public class BrickFactory {

    private static BrickFactory _brickFactoryInstance;

    /**
     * Builder actions generate a voxel brick under the current parameters.
     */
    interface BuilderAction {
        void build(VoxTree tree, Texture texture);
    }
    private final BuilderAction[] builderActions;

    /** Floating point equivalence */
    private static double EPSILON = 1e-9;

    /** Texture generator */
    private Texture texture;

    /** Parameter indices and booleans */
    private String name;
    private int typeIdx = 0;
    private int scaleIdx = 0;
    private int decayIdx = 0;
    private int bandIdx = 0;
    private int quantIdx = 0;
    private boolean invert = false;
    private boolean reflect = false;
    private boolean square = false;

    /** Parameter values in reasonable settings */
    private static final double[] scaleValue = {1.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.5, 0.75 };
    private static final double[] decayValue = {0.0, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0 };
    private static final double[] bandValue =  {0.0, 0.0, 0.1, 0.25, 0.5, 0.75, 0.90, 0.95 };
    private static final int[]    quantValue = {1, 2, 3, 4, 5, 6, 7, 8 };

    /** Point buffer */
    private static Point3i voxPoint = new Point3i();

    public static BrickFactory instance() {
        if (_brickFactoryInstance == null) {
            _brickFactoryInstance = new BrickFactory();
        }

        return _brickFactoryInstance;
    }

    private BrickFactory() {
        texture =  new Texture();
        name = "test";

        builderActions = new BuilderAction[] {
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Coal(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Stone(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Dirt(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Steel(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
                new BuilderAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } }
        };
    }

    /**
     * Returns our texture object, complete with the current parametric settings, so that the texture
     * can be applied in other contexts.
     *
     * @return      The texture generation object.
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Return the name of the currently generated brick, though "test" is the default.
     *
     * @return      Brick name.
     */
    public String name() {
        return name;
    }

    /**
     * Process a Control-Key event and, if the key is relevant to our interests, regenerate
     * the texture brick into the given {@link VoxTree}.  This will also erase any voxels
     * currently  in the tree.
     *
     * @param key       Key event to process
     * @param tree      VoxTree to generate the brick into.
     */
    public void keyPressed(KeyEvent key, VoxTree tree){
        // Control must be down for all Brick Factory keys
        if (key.getKeyCode() == KeyEvent.VK_CONTROL) {
            return;
        }

        if (key.isShiftDown()) {
            // --------------------------------------
            // Shifted events cycle backwards through the options, or set
            // particular parameters.
            // --------------------------------------
            switch (key.getKeyCode()) {
                case KeyEvent.VK_Q:
                    quantIdx = (quantIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_S:
                    scaleIdx = (scaleIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_B:
                    bandIdx = (bandIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_D:
                    decayIdx = (decayIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_R:
                    square = !square;
                    break;
            }

        } else {
            // --------------------------------------
            // Unshifted events cycle forwards through the options, or set
            // particular parameters.
            // --------------------------------------
            switch (key.getKeyCode()) {
                case KeyEvent.VK_1:
                    typeIdx = 0;
                    break;
                case KeyEvent.VK_2:
                    typeIdx = 1;
                    break;
                case KeyEvent.VK_3:
                    typeIdx = 2;
                    break;
                case KeyEvent.VK_4:
                    typeIdx = 3;
                    break;
                case KeyEvent.VK_5:
                    typeIdx = 4;
                    break;
                case KeyEvent.VK_6:
                    typeIdx = 5;
                    break;
                case KeyEvent.VK_7:
                    typeIdx = 6;
                    break;
                case KeyEvent.VK_8:
                    typeIdx = 7;
                    break;
                case KeyEvent.VK_9:
                    typeIdx = 8;
                    break;
                case KeyEvent.VK_0:
                    typeIdx = 9;
                    break;
                case KeyEvent.VK_Q:
                    quantIdx = (quantIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_S:
                    scaleIdx = (scaleIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_B:
                    bandIdx = (bandIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_D:
                    decayIdx = (decayIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_I:
                    invert = !invert;
                    break;
                case KeyEvent.VK_R:
                    reflect = !reflect;
                    break;
            }
        }

        // --------------------------------------
        // Set the texture generation parameters and control flgas
        // --------------------------------------
        texture.inputScale = scaleValue[scaleIdx];
        texture.quantNum = quantValue[quantIdx];
        texture.band = bandValue[bandIdx];
        texture.curlScale = decayValue[decayIdx];

        texture.transform = 0;
        if (quantIdx > 0) texture.transform |= Texture.QUANT;
        if (bandIdx > 0) texture.transform |= Texture.BANDCLAMP;
        if (decayIdx > 0) texture.transform |= Texture.CURL;
        if (invert) texture.transform |= Texture.INVERT;
        if (reflect) texture.transform |= Texture.REFLECT;
        if (square) texture.transform |= Texture.SQUARE;

        // Log the parameters so we can see what we are doing
        System.out.println("BRICK # " + typeIdx +
                " inputScale: " + (scaleIdx == 0 ? "OFF" : texture.inputScale) +
                ", band: " + (bandIdx == 0 ? "OFF" : texture.band) +
                ", curlScale: " + (decayIdx == 0 ? "OFF" : texture.curlScale) +
                ", quant: " + (quantIdx == 0 ? "OFF" : texture.quantNum) +
                ", invert " + invert +
                ", reflect " + reflect +
                ", square " + square );

        // --------------------------------------
        // Build the brick
        // --------------------------------------
        builderActions[typeIdx].build(tree, texture);
    }

    /**
     * Serialize the factory texture parameters into a JSON object
     *
     * TODO: Convert to Jackson automatic processing
     * TODO: Name of the brick?
     *
     * @return  The JSON string representing the factory parameters
     */
    public String serializeJSON() {
        JsonFactory jsonFactory = new JsonFactory();
        StringWriter output = new StringWriter();

        try {
            JsonGenerator gen = jsonFactory.createJsonGenerator(output);

            gen.writeStartObject();
            gen.writeStringField("version", "1.0.0");
            gen.writeNumberField("transform", texture.transform);
            gen.writeNumberField("quantize", texture.quantNum);
            gen.writeNumberField("inputScale", texture.inputScale);
            gen.writeNumberField("band", texture.band);
            gen.writeNumberField("curlScale", texture.curlScale);
            gen.writeEndObject();

            gen.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        return output.toString();

    }

    /**
     * Interpret a JSON string that should hold an object of parameters
     * for the factory texture generator, setting those parameters in the factory's texture.
     *
     * @param json      JSON object holding the factory parameters
     */
    public void deserializeJSON(String json) {
        StringReader input = new StringReader(json);
        int size = 0;

        JsonFactory jsonFactory = new JsonFactory();
        try {
            JsonParser parse = jsonFactory.createJsonParser(input);

            if (parse.nextToken() != JsonToken.START_OBJECT) {
                throw new Exception("Serialized data must begin with a START_OBJECT");
            }
            while (parse.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parse.getCurrentName();
                parse.nextToken(); // move to value

                if ("version".equals(fieldName)) {
                    if (!parse.getText().equals("1.0.0")) {
                        throw new Exception("We only know how to parse version 1.0.0 at this time: " + parse.getText() + " is not valid.");
                    }
                } else if ("transform".equals(fieldName)) {
                    texture.transform = parse.getIntValue();
                } else if ("quantize".equals(fieldName)) {
                    texture.quantNum = parse.getIntValue();
                } else if ("inputScale".equals(fieldName)) {
                    texture.inputScale = parse.getDoubleValue();
                } else if ("band".equals(fieldName)) {
                    texture.band = parse.getDoubleValue();
                } else if ("curlScale".equals(fieldName)) {
                    texture.curlScale = parse.getDoubleValue();
                }
            }
            parse.close(); // ensure resources get clean

            scaleIdx = findIndexForValue(texture.inputScale, scaleValue);
            decayIdx = findIndexForValue(texture.curlScale, decayValue);
            bandIdx = findIndexForValue(texture.band, bandValue);
            quantIdx = findIndexForValue(texture.quantNum, quantValue);

            return;

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * Serialize the factory texture parameters into a byte array
     *
     * @return  The byte array representing the factory parameters
     */
    public byte[] serializeBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            String version = "1.0.0";

            output.write(Bytes.toBytes(version.length()));
            output.write(Bytes.toBytes(version));
            output.write(Bytes.toBytes(texture.transform));
            output.write(Bytes.toBytes(texture.quantNum));
            output.write(Bytes.toBytes(texture.inputScale));
            output.write(Bytes.toBytes(texture.band));
            output.write(Bytes.toBytes(texture.curlScale));

        } catch (Exception e) {
            System.out.println(e);
        }

        return output.toByteArray();
    }

    public void deserializeBytes(byte[] source) {
        ByteArrayInputStream input = new ByteArrayInputStream(source);
        int size = 0;

        byte[] buf = new byte[8];

        String version;
        int strLen;

        try {
            input.read(buf, 0, Integer.SIZE/8);
            strLen = Bytes.toInt(buf);

            input.read(buf, 0, strLen);
            version = Bytes.toString(buf);

            if (!version.equals("1.0.0")) {
                throw new Exception("We only know how to parse version 1.0.0 at this time: " + version + " is not valid.");
            }

            input.read(buf, 0, Integer.SIZE/8);
            texture.transform = Bytes.toInt(buf);

            input.read(buf, 0, Integer.SIZE/8);
            texture.quantNum = Bytes.toInt(buf);

            input.read(buf, 0, Double.SIZE/8);
            texture.inputScale = Bytes.toDouble(buf);

            input.read(buf, 0, Double.SIZE/8);
            texture.band = Bytes.toDouble(buf);

            input.read(buf, 0, Double.SIZE/8);
            texture.curlScale = Bytes.toDouble(buf);

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * Given a parameter value, find the index that best represents it in an array
     * of possible values.
     *
     * @param value     Value to find in an array
     * @param values    Array of possible values to search
     * @return          The index of the value as found in the array of values
     */
    private int findIndexForValue(double value, double[] values) {
        int index = 0;
        double distance;
        double minDist = Double.MAX_VALUE;
        int minIndex = -1;

        for (double test : values) {
            distance = Math.abs(test - value);
            // If we hit it dead on, return early
            if (distance < EPSILON) {
                return index;
            } else if (distance < minDist) {
                minIndex = index;
                minDist = distance;
            }
            ++index;
        }
        return minIndex;
    }

    /**
     * Given a parameter value, find the index that best represents it in an array
     * of possible values.
     *
     * @param value     Value to find in an array
     * @param values    Array of possible values to search
     * @return          The index of the value as found in the array of values
     */
    private int findIndexForValue(int value, int[] values) {
        int index = 0;
        int distance;
        int minDist = Integer.MAX_VALUE;
        int minIndex = -1;

        for (int test : values) {
            distance = Math.abs(test - value);
            // If we hit it dead on, return early
            if (distance == 0) {
                return index;
            } else if (distance < minDist) {
                minIndex = index;
                minDist = distance;
            }
            ++index;
        }
        return minIndex;
    }

    /**
     * Determine if a given coordinate is on the skin of the voxel tree's extent, given
     * a skin thickness (in leaf voxels).
     *
     * @param tree    Tree that we are operating on, which defines the total extents
     * @param x       X ordinate to set
     * @param y       Y ordinate to set
     * @param z       Z ordinate to set
     * @param w       Skin thickness (1 or larger, typically 1 or 2)
     * @return        True if the coordinate is within the "skin" layer of the voxel tree
     */
    private boolean isSkin(VoxTree tree, int x, int y, int z, int w) {
        int edge = tree.breadth() - 1;

        if ( ( x < w )
          || ( x > (edge-w) )
          || ( y < w )
          || ( y > (edge-w) )
          || ( z < w )
          || ( z > (edge-w) )
          ) {
            return true;
        }

        return false;
    }

    /**
     * Set a voxel in the tree, but only set the voxel to the indicated value if
     * it is in the skin of the tree... interior voxels are set to empty.
     *
     * @param tree        Tree that we are operating upon, to set the voxel in
     * @param x           X ordinate to set
     * @param y           Y ordinate to set
     * @param z           Z ordinate to set
     * @param w           Skin thickness (1 or larger)
     * @param material    Material value to set the voxel to
     */
    private void setSkin(VoxTree tree, int x, int y, int z, int w, long material) {
        int stride = tree.stride();
        int offset = stride >> 1;

        voxPoint.set( x*stride + offset, y*stride + offset, z*stride + offset);

        if (isSkin(tree, x, y, z, w)) {
            tree.setVoxelPoint(voxPoint, material);
        } else {
            tree.setVoxelPoint(voxPoint,  Material.setMaterial(0, 0, 0, 255, 0, 0));
        }
    }


    public void Test(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        name = "test";

        int stride = newTree.stride();
        int offset = stride >> 1;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    newTree.setVoxelPoint(new Point3i((x*stride)+offset, (y*stride)+offset, (z*stride)+offset),
                            Material.setMaterial(255, 64, 64, 8, 128, 255));
//                            Material.setMaterial(0x34, 0x1c, 0x02, 0xff, 128, 32));       // Dark brown
//                            Material.setMaterial(0xe6, 0xda, 0xa6, 0xFF, 128, 32));         // Yellowish
//                            Material.setMaterial(0x96, 0x4b, 0x00, 0xff, 128, 192));         // Med brown
//                            Material.setMaterial(0xc9, 0xb0, 0x03, 0xF0, 192, 224));        // Yellow
//                            Material.setMaterial(0xE0, 0xDF, 0xDb, 255, 224, 255));        // Grey
//                            Material.setMaterial(0xC6, 0xE2, 0xFF, 255, 224, 255));        // Steel Grey
//                            Material.setMaterial(0x6c, 0x79, 0x8b, 255, 224, 255));        //  Grey

                }
            }
        }

        tree.setPool(newTree.tilePool());
    }

    public void Coal(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        name = "coal";

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    int density = Texture.toByte(texture.value(x, y, z));

                    long color1 = Material.setMaterial(0xE0, 0xE0, 0xE0, 164, 192, 255);
                    long color2 = Material.setMaterial(30, 30, 30, density, 64, 0);

                    long color = Material.alphaBlend(color2, color1);


                    setSkin(newTree, x, y, z, 2, color);
                }
            }
        }

        tree.setPool(newTree.tilePool());
    }

    public void Stone(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        name = "stone";

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    double density = texture.value(x, y, z);

                    long color = Material.gradient(
                            Material.setMaterial(128, 128, 128, 255, 255, 32),
                            Material.setMaterial(240, 240, 240, 255, 192, 32),
                            density);

                    density = texture.value(x*2, y*2, z*2);
                    if (density > 0.90) {
                        // Sparkles
                        color = Material.setMaterial(224, 224, 255, 192, 128, 255);
                    }

                    setSkin(newTree, x, y, z, 2, color);
                }
            }
        }

        tree.setPool(newTree.tilePool());
    }

    public void Dirt(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        name = "dirt";

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    double density = texture.value(x, y, z);
                    long color1 = Material.gradient(
                            Material.setMaterial(0x34, 0x1c, 0x02, 0xff, 128, 32),
                            Material.setMaterial(0xe6, 0xda, 0xa6, 0xFF, 128, 32), density);

                    density = texture.value(x*1.5, y*1.5, z*1.5);
                    long color2 = Material.gradient(
                            Material.setMaterial(0x96, 0x4b, 0x00, 0xff, 128, 192),
                            Material.setMaterial(0xc9, 0xb0, 0x03, 0xF0, 192, 224), density);

//                            Material.setMaterial(0x34, 0x1c, 0x02, 0xff, 128, 32));       // Dark brown
//                            Material.setMaterial(0xe6, 0xda, 0xa6, 0xFF, 128, 32));         // Yellowish
//                            Material.setMaterial(0x96, 0x4b, 0x00, 0xff, 128, 192));         // Med brown
//                            Material.setMaterial(0xc9, 0xb0, 0x03, 0xF0, 192, 224));        // Yellow

                    texture.inputScale *= 0.5;
                    density = texture.value(x*0.5, y*0.5, z*0.5);
                    texture.inputScale *= 2.0;
                    setSkin(newTree, x, y, z, 2, Material.gradient(color1, color2, density));
                }
            }
        }

        tree.setPool(newTree.tilePool());
    }

    public void Steel(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        name = "steel";

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    double density = texture.value(x, z, x);

                    long color = Material.gradient(
                            Material.setMaterial(0xC6, 0xE2, 0xFF, 255, 224, 255),        // Steel Grey
                            Material.setMaterial(0x6c, 0x79, 0x8b, 255, 240, 240),        //  Grey
                            density);

/*
                    density = texture.value(x*2, y*2, z*2);
                    if (density > 0.90) {
                        // Sparkles
                        color = Material.setMaterial(224, 224, 255, 192, 128, 255);
                    }
*/

                    setSkin(newTree, x, y, z, 2, color);
                }
            }
        }

        tree.setPool(newTree.tilePool());
    }

}
