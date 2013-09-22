package com.simreal.VoxEngine;

import java.util.Formatter;

class Node {
/**
 * 64 bit node bits:
 *      8 red
 *      8 green
 *      8 blue
 *      8 alpha
 *      24 sub-node index in array
 *      4 flags
 *      4 depth
 */
    private static final long NODE_RGBA_MASK        = 0x00000000FFFFFFFFL;
    private static final long NODE_BLUE_MASK        = 0x00000000000000FFL;
    private static final long NODE_GREEN_MASK       = 0x000000000000FF00L;
    private static final long NODE_RED_MASK         = 0x0000000000FF0000L;
    private static final long NODE_ALPHA_MASK       = 0x00000000FF000000L;
    private static final long NODE_CHILD_MASK       = 0x00FFFFFF00000000L;
    private static final long NODE_FLAG_LEAF_MASK   = 0x0100000000000000L;
    private static final long NODE_DEPTH_MASK       = 0xF000000000000000L;

    private static final byte NODE_RGBA_SHIFT       = 0;
    private static final byte NODE_BLUE_SHIFT       = 0;
    private static final byte NODE_GREEN_SHIFT      = 8;
    private static final byte NODE_RED_SHIFT        = 16;
    private static final byte NODE_ALPHA_SHIFT      = 24;
    private static final byte NODE_CHILD_SHIFT      = 32;
    private static final byte NODE_DEPTH_SHIFT      = 60;

    static long setColor(long node, int red, int green, int blue, int alpha){
        long rgba = ((long)red << NODE_RED_SHIFT)
                | ((long)green << NODE_GREEN_SHIFT)
                | ((long)blue << NODE_BLUE_SHIFT)
                | ((long)alpha << NODE_ALPHA_SHIFT);
        return (node & ~NODE_RGBA_MASK) | rgba;
    }

    static long setColor(long node, long rgba){
        return (node & ~NODE_RGBA_MASK) | ((rgba & 0xFFFFFFFFL) << NODE_RGBA_SHIFT);
    }

    static int red(long node){
        return (int)((node & NODE_RED_MASK) >>> NODE_RED_SHIFT);
    }

    static int green(long node){
        return (int)((node & NODE_GREEN_MASK) >>> NODE_GREEN_SHIFT);
    }

    static int blue(long node){
        return (int)((node & NODE_BLUE_MASK) >>> NODE_BLUE_SHIFT);
    }

    static int alpha(long node){
        return (int)((node & NODE_ALPHA_MASK) >>> NODE_ALPHA_SHIFT);
    }

    static long color(long node){
        return (node & NODE_RGBA_MASK) >>> NODE_RGBA_SHIFT;
    }

    static long setChild(long node, int child){
        return (node & ~NODE_CHILD_MASK)
                | ((long)child << NODE_CHILD_SHIFT);
    }

    static int child(long node){
        return (int)((node & NODE_CHILD_MASK) >>> NODE_CHILD_SHIFT);
    }

    static long setLeaf(long node, boolean leaf){
        if (leaf){
            return (node | NODE_FLAG_LEAF_MASK);
        }
        return (node & ~NODE_FLAG_LEAF_MASK);
    }

    static boolean isLeaf(long node){
        return (node & NODE_FLAG_LEAF_MASK) == NODE_FLAG_LEAF_MASK;
    }

    static long setDepth(long node, byte depth){
        return (node & ~NODE_DEPTH_MASK)
                | ((long)depth << NODE_DEPTH_SHIFT);
    }

    static byte depth(long node){
        return (byte)((node & NODE_DEPTH_MASK) >>> NODE_DEPTH_SHIFT);
    }

    static String toString(long node){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();
        String NEW_LINE = System.getProperty("line.separator");

        if (Node.isLeaf(node)) {
            result.append("LEAF {");
        } else {
            result.append("NODE {");
        }
        fmt.format("%08X", Node.color(node));
        result.append("   Color: ").append(fmt.toString());
        result.append("   Depth: ").append(Node.depth(node));
        result.append("   Child: ").append(Node.child(node));
        result.append("}").append(NEW_LINE);
        return result.toString();
    }

}
