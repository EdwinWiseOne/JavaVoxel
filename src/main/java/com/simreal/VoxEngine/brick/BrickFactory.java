package com.simreal.VoxEngine.brick;

import com.simreal.VoxEngine.Material;
import com.simreal.VoxEngine.Texture;
import com.simreal.VoxEngine.VoxTree;

import javax.vecmath.Point3i;
import java.awt.event.KeyEvent;
import java.util.jar.Attributes;

public class BrickFactory {

    interface BuildAction {
        void build(VoxTree tree, Texture texture);
    }

    private static final BuildAction[] buildActions = new BuildAction[] {
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Coal(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Stone(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Dirt(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Steel(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Test(tree, texture); } },
    };

    private static VoxTree _tree = null;
    private static Texture _texture = new Texture();
    private static String _name = "test";

    private static int typeIdx = 0;

    private static int scaleIdx = 0;
    private static int decayIdx = 0;
    private static int bandIdx = 0;
    private static int quantIdx = 0;
    private static boolean invert = false;
    private static boolean reflect = false;
    private static boolean square = false;

    private static final double[] scaleValue = {1.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.5, 0.75 };
    private static final double[] decayValue = {0.0, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0 };
    private static final double[] bandValue =  {0.0, 0.0, 0.1, 0.25, 0.5, 0.75, 0.90, 0.95 };
    private static final int[]    quantValue = {1, 2, 3, 4, 5, 6, 7, 8 };

    private static Point3i voxPoint = new Point3i();

    public static Texture texture() {
        return _texture;
    }

    public static String name() {
        return _name;
    }

    public static void keyPressed(KeyEvent e, VoxTree tree){
        // Control is down for all Brick Factory events

        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            return;
        }

        if (e.isShiftDown()) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_Q:
                    quantIdx = (quantIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_L:
                    bandIdx = (bandIdx - 1) & 0x07;
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
            switch (e.getKeyCode()) {
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
                case KeyEvent.VK_L:
                    bandIdx = (bandIdx + 1) & 0x07;
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

//        Texture _texture = new Texture();
        _texture.transform = 0;
        if (quantIdx > 0) _texture.transform |= Texture.QUANT;
        if (bandIdx > 0) _texture.transform |= Texture.BANDCLAMP;
        if (decayIdx > 0) _texture.transform |= Texture.CURL;
        if (invert) _texture.transform |= Texture.INVERT;
        if (reflect) _texture.transform |= Texture.REFLECT;
        if (square) _texture.transform |= Texture.SQUARE;

        _texture.scale = scaleValue[scaleIdx];
        _texture.quantLevel = quantValue[quantIdx];
        _texture.band = bandValue[bandIdx];
        _texture.decay = decayValue[decayIdx];

        System.out.println("BRICK # " + typeIdx +
                " scale: " + _texture.scale +
                ", band: " + _texture.band +
                ", decay: " + _texture.decay +
                ", quant: " + _texture.quantLevel +
                ", invert " + invert +
                ", reflect " + reflect +
                ", square " + square );

        _tree = tree;
        buildActions[typeIdx].build(tree, _texture);
    }

    public static void save() {
        Attributes tags = new Attributes();

        tags.put(new Attributes.Name("scale"), new Double(_texture.scale).toString());
        tags.put(new Attributes.Name("decay"), new Double(_texture.decay).toString());
        tags.put(new Attributes.Name("band"), new Double(_texture.band).toString());
        tags.put(new Attributes.Name("quant"), new Integer(_texture.quantLevel).toString());
        tags.put(new Attributes.Name("transform"), new Integer(_texture.transform).toString());

        _tree.save(_name, tags);
    }


    private static boolean isSkin(VoxTree tree, int x, int y, int z, int w) {
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

    private static void setSkin(VoxTree tree, int x, int y, int z, int w, long material) {
        int stride = tree.stride();
        int offset = stride >> 1;

        voxPoint.set( x*stride + offset, y*stride + offset, z*stride + offset);

        if (isSkin(tree, x, y, z, w)) {
            tree.setVoxelPoint(voxPoint, material);
        } else {
            tree.setVoxelPoint(voxPoint,  Material.setMaterial(0, 0, 0, 255, 0, 0));
        }
    }


    public static void Test(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "test";

        int stride = newTree.stride();
        int offset = stride >> 1;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {
                    newTree.setVoxelPoint(new Point3i((x*stride)+offset, (y*stride)+offset, (z*stride)+offset),
                            Material.setMaterial(255, 64, 64, 192, 128, 255));
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

        tree.setPool(newTree.nodePool());
    }

    public static void Coal(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "coal";

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    int density = Texture.toByte(BrickFactory.texture().value(x, y, z));

                    long color1 = Material.setMaterial(0xE0, 0xE0, 0xE0, 164, 192, 255);
                    long color2 = Material.setMaterial(30, 30, 30, density, 64, 0);

                    long color = Material.blend(color2, color1);


                    setSkin(newTree, x, y, z, 2, color);
                }
            }
        }

        tree.setPool(newTree.nodePool());
    }

    public static void Stone(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "stone";

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

        tree.setPool(newTree.nodePool());
    }

    public static void Dirt(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "dirt";

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

                    texture.scale *= 0.5;
                    density = texture.value(x*0.5, y*0.5, z*0.5);
                    texture.scale *= 2.0;
                    setSkin(newTree, x, y, z, 2, Material.gradient(color1, color2, density));
                }
            }
        }

        tree.setPool(newTree.nodePool());
    }

    public static void Steel(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "steel";
        _name = "steel";

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

        tree.setPool(newTree.nodePool());
    }

}
