package com.simreal.VoxEngine.brick;

import com.simreal.VoxEngine.Color;
import com.simreal.VoxEngine.Texture;
import com.simreal.VoxEngine.VoxTree;

import javax.vecmath.Point3i;
import java.awt.event.KeyEvent;

public class BrickFactory {

    interface BuildAction {
        void build(VoxTree tree, Texture texture);
    }

    private static final BuildAction[] buildActions = new BuildAction[] {
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Coal(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Stone(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Dirt(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
            new BuildAction() { public void build(VoxTree tree, Texture texture) { Black(tree, texture); } },
    };

    private static Texture _texture = new Texture();
    private static String _name = "test";

    private static int typeIdx = 0;

    private static int scaleIdx = 0;
    private static int decayIdx = 0;
    private static int threshIdx = 0;
    private static int levelIdx = 0;
    private static int quantIdx = 0;
    private static boolean invert = false;
    private static boolean reflect = false;
    private static boolean square = false;


    private static final double[] scaleValue = {1.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.5, 0.75 };
    private static final double[] decayValue = {0.0, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0 };
    private static final double[] threshValue = {0.0, 0.0, 0.1, 0.25, 0.5, 0.75, 0.90, 0.95 };
    private static final double[] levelValue =  {0.0, 0.0, 0.1, 0.25, 0.5, 0.75, 0.90, 0.95 };
    private static final int[]    quantValue = {1, 2, 3, 4, 5, 6, 7, 8 };

    private static Point3i voxPoint = new Point3i();

    public static Texture texture() {
        return _texture;
    }

    public static String name() {
        return _name;
    }

    public static void keyPressed(KeyEvent e, VoxTree tree){

        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            return;
        }

        if (e.isShiftDown()) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_Q:
                    quantIdx = (quantIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_L:
                    levelIdx = (levelIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_S:
                    scaleIdx = (scaleIdx - 1) & 0x07;
                    break;
                case KeyEvent.VK_T:
                    threshIdx = (threshIdx - 1) & 0x07;
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
                    levelIdx = (levelIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_S:
                    scaleIdx = (scaleIdx + 1) & 0x07;
                    break;
                case KeyEvent.VK_T:
                    threshIdx = (threshIdx + 1) & 0x07;
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
        if (levelIdx > 0) _texture.transform |= Texture.TAN2CLAMP;
        if (threshIdx > 0) _texture.transform |= Texture.THRESH;
        if (decayIdx > 0) _texture.transform |= Texture.CURL;
        if (invert) _texture.transform |= Texture.INVERT;
        if (reflect) _texture.transform |= Texture.REFLECT;
        if (square) _texture.transform |= Texture.SQUARE;

        _texture.scale = scaleValue[scaleIdx];
        _texture.quantLevel = quantValue[quantIdx];
        _texture.seaLevel = levelValue[levelIdx];
        _texture.threshold = threshValue[threshIdx];
        _texture.decay = decayValue[decayIdx];

        System.out.println("BRICK # " + typeIdx +
                " scale: " + _texture.scale +
                ", level: " + _texture.seaLevel +
                ", threshold: " + _texture.threshold +
                ", decay: " + _texture.decay +
                ", quant: " + _texture.quantLevel +
                ", invert " + invert +
                ", reflect " + reflect +
                ", square " + square );

        buildActions[typeIdx].build(tree, _texture);
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

        return true;
    }

    private static void setSkin(VoxTree tree, int x, int y, int z, int w, int color) {
        int stride = tree.stride();
        int offset = stride >> 1;

        voxPoint.set( x*stride + offset, y*stride + offset, z*stride + offset);

        if (isSkin(tree, x, y, z, w)) {
            tree.setVoxelPoint(voxPoint, color);
        } else {
            tree.setVoxelPoint(voxPoint, (int)Color.setColor(0,0,0,255));
        }
//            tree.setVoxelPoint(voxPoint, color);
    }


    public static void Black(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "void";

        int stride = newTree.stride();
        int offset = stride >> 1;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {
                    newTree.setVoxelPoint(new Point3i((x*stride)+offset, (y*stride)+offset, (z*stride)+offset), (int) Color.setColor(30, 30, 30, 255));
                }
            }
        }

//        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

    public static void Coal(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "coal";

//        _texture.transform |= Texture.SQUARE;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {
                    // TODO: If it's not on the skin layer(s) set to zero color

                    double density = texture.value(x, y, z);

                    int color1 = (int)Color.setColor(0xC0, 0xC0, 0xC0, 255);
                    int color2 = (int)Color.setColor(30, 30, 30, Texture.toByte(BrickFactory.texture().value(x, y, z)));

                    int color = (int)Color.blend(color2, color1);


                    setSkin(newTree, x, y, z, 1, color);
                }
            }
        }

//        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

    public static void Stone(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "stone";

//        _texture.transform |= Texture.SQUARE;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {
                    double density = texture.value(x, y, z);
                    int color = (int)Color.gradient(Color.setColor(128, 128, 128, 255), Color.setColor(240, 240, 240, 255), density);

                    density = texture.value(x*2, y*2, z*2);
                    if (density > 0.90) {
                        // Sparkles
                        color = (int)Color.setColor(240, 240, 255, 192);
                    }

                    setSkin(newTree, x, y, z, 2, color);
                }
            }
        }

//        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

    public static void Dirt(VoxTree tree, Texture texture) {
        VoxTree newTree = new VoxTree(tree.depth());

        _name = "dirt";

//        _texture.transform |= Texture.SQUARE;

        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                for (int z=0; z<tree.breadth(); ++z) {

                    double density = texture.value(x, y, z);
                    int color1 = (int)Color.gradient(Color.setColor(0x34, 0x1c, 0x02, 0xff), Color.setColor(0xe6, 0xda, 0xa6, 0xFF), density);

                    density = texture.value(x*1.5, y*1.5, z*1.5);
                    int color2 = (int)Color.gradient(Color.setColor(0x96, 0x4b, 0x00, 0xff), Color.setColor(0xc9, 0xb0, 0x03, 0xE0), density);

                    texture.transform &= ~Texture.CURL;
                    density = texture.value(x*0.5, y*0.5, z*0.5);

                    setSkin(newTree, x, y, z, 2, (int) Color.gradient(color1, color2, density));
                }
            }
        }

//        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

}
