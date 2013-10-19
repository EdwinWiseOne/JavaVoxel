package com.simreal.VoxEngine;

import com.simreal.VoxEngine.annotations.Stateless;

@Stateless
class Node {
    /**
     * Node bit manipulations
     *
     * A Node is an entry in the Voxel Octree (SVO), which is aware of its
     * depth in the tree, has a pointer to its children tile (which holds all eight
     * children)..
     *
     *    +-------+-------+-------+-------+-------+-------+-------+-------+
     *    |depth  | flags | child                                         |
     *    +-------+-------+-------+-------+-------+-------+-------+-------+
     * 32               24              16               8               0
     */
    private static final int CHILD_MASK         = 0x00FFFFFF;
    private static final int FLAG_LEAF_MASK     = 0x01000000;
    private static final int FLAG_USED_MASK     = 0x02000000;
//    private static final int FLAG_TOBRICK_MASK = 0x04000000;
    private static final int DEPTH_MASK         = 0xF0000000;

    private static final byte CHILD_SHIFT   = 0;
    private static final byte DEPTH_SHIFT   = 28;

    public static final int END_OF_FREE_NODES = 0;


    static int setChild(int node, int child){
        return (node & ~CHILD_MASK)
                | (child << CHILD_SHIFT);
    }

    static int child(int node){
        return ((node & CHILD_MASK) >>> CHILD_SHIFT);
    }

    static int setLeaf(int node, boolean leaf){
        if (leaf){
            return (node | FLAG_LEAF_MASK);
        }
        return (node & ~FLAG_LEAF_MASK);
    }

    static boolean isLeaf(int node){
        return (node & FLAG_LEAF_MASK) == FLAG_LEAF_MASK;
    }

    static boolean isNode(int node){
        return (node & FLAG_LEAF_MASK) == 0;
    }

    static int setUsed(int node, boolean used){
        if (used){
            return (node | FLAG_USED_MASK);
        }
        return (node & ~FLAG_USED_MASK);
    }

    static boolean isUsed(int node){
        return (node & FLAG_USED_MASK) == FLAG_USED_MASK;
    }

/*
    static long setToBrick(int node, boolean toBrick){
        if (toBrick){
            return (node | FLAG_TOBRICK_MASK);
        }
        return (node & ~FLAG_TOBRICK_MASK);
    }

    static boolean isToBrick(int node){
        return (node & FLAG_TOBRICK_MASK) == FLAG_TOBRICK_MASK;
    }
*/


    static int setDepth(int node, byte depth){
        return (node & ~DEPTH_MASK)
                | ((int)depth << DEPTH_SHIFT);
    }

    static byte depth(int node){
        return (byte)((node & DEPTH_MASK) >>> DEPTH_SHIFT);
    }

    static String toString(int node){
        StringBuilder result = new StringBuilder();
//        Formatter fmt = new Formatter();
//        String NEW_LINE = System.getProperty("line.separator");

        if (Node.isLeaf(node)) {
            result.append("LEAF {");
        } else {
            result.append("NODE {");
        }
        result.append(" Depth: ").append(Node.depth(node));
        result.append(", Child: ").append(Node.child(node));
        result.append(" }");
        return result.toString();
    }

}
