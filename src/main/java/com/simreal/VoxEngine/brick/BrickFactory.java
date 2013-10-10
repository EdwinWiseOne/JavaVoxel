package com.simreal.VoxEngine.brick;

import com.simreal.VoxEngine.Color;
import com.simreal.VoxEngine.Texture;
import com.simreal.VoxEngine.VoxTree;

import javax.vecmath.Point3i;

public class BrickFactory {

    public static void Black(VoxTree tree) {
        VoxTree newTree = new VoxTree(tree.depth());

        int stride = newTree.stride();
        int offset = stride >> 1;

        for (int x=0; x<tree.edgeLength(); ++x){
            for (int y=0; y<tree.edgeLength(); ++y){
                for (int z=0; z<tree.edgeLength(); ++z) {
                    newTree.setVoxelPoint(new Point3i((x*stride)+offset, (y*stride)+offset, (z*stride)+offset), (int) Color.setColor(30, 30, 30, 255));
                }
            }
        }

        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

    public static void Coal(VoxTree tree) {
        VoxTree newTree = new VoxTree(tree.depth());

        Texture texture = new Texture();
        texture.

        int stride = newTree.stride();
        int offset = stride >> 1;

        for (int x=0; x<tree.edgeLength(); ++x){
            for (int y=0; y<tree.edgeLength(); ++y){
                for (int z=0; z<tree.edgeLength(); ++z) {
                    newTree.setVoxelPoint(new Point3i((x*stride)+offset, (y*stride)+offset, (z*stride)+offset), (int) Color.setColor(0, 0, 0, 255));
                }
            }
        }

        System.out.println(newTree.toString());

        tree.setPool(newTree.nodePool());
    }

}
