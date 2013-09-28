package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;
import java.util.Formatter;

@Stateless
public class Path {
    /**
     * Path bit manipulations.
     * Stateless, Immutable
     *
     * The Path holds the child-choice decisions used to reach
     * a given Node.  It is composed of two parts - the 3-bit child indices
     * selected from the root node to the given node, and the tree depth of
     * the node selected.  Only the first n=depth child selections count, and
     * if the given depth is less than the tree depth, it represents a parent
     * node and not a leaf.
     *
     * A Path is atomic to 64-bit operations.
     *
     *
     *  64            56            48            40            32
     *    +------+------+------+------+------+------+------+------+
     *    | child[16] : 3-bits * 16 = 48                          |
     *    +------+------+------+------+------+------+------+------+
     *    |                           | n/a         | n/a  |depth |
     *    +------+------+------+------+------+------+------+------+
     *                24            16             8             0
     */
    // TODO: Create a path iterator

    public static final int PATH_MAX_DEPTH     = 15;

    private static final long PATH_DEPTH_MASK   = 0x00000000000000FFL;
    private static final long PATH_PATH_MASK    = 0xFFFFFFFFFFFF0000L;
    private static final long PATH_HEAD_MASK    = 0xE000000000000000L;

    private static final long PATH_CHILD_MASK   = 0x0000000000000007L;

    private static final byte PATH_DEPTH_SHIFT  = 0;
    private static final byte PATH_PATH_SHIFT   = 16;
    private static final byte PATH_HEAD_SHIFT   = 61;


    public static int depth(long path){
        int ret = (int)((path & PATH_DEPTH_MASK) >>> PATH_DEPTH_SHIFT);
        return ret;
    }

    public static int child(long path, int depth) {
        int offset = 3*depth;
        long mask = PATH_HEAD_MASK >>> offset;
        long shift = PATH_HEAD_SHIFT - offset;

        int ret = (int)((path & mask) >>> shift);
        return ret;
    }

    private static long setChild(long path, int depth, int child) {
        int offset = 3*depth;
        long mask = PATH_HEAD_MASK >>> offset;
        long shift = PATH_HEAD_SHIFT - offset;

        /*
        long t1 = path & ~mask;
        long t2 = (child & PATH_CHILD_MASK);
        long t3 = t2 << shift;
        long t4 = t1 | t3;
        */
        return (path & ~mask) | ((child & PATH_CHILD_MASK)  << shift);
    }

    public static long setDepth(long path, int depth) {
        /*
        long t1 = path & ~PATH_DEPTH_MASK;
        long t2 = depth << PATH_DEPTH_SHIFT;
        long t3 = t1 | t2;
        */

        return (path & ~PATH_DEPTH_MASK) | (depth << PATH_DEPTH_SHIFT);
    }

    public static long addChild(long path, int child) {
        int depth = Path.depth(path);

        if (depth >= PATH_MAX_DEPTH) return path;

        return setDepth(setChild(path, depth, child), depth+1);
    }

    static String toString(long path){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();
        String NEW_LINE = System.getProperty("line.separator");

        int depth = Path.depth(path);
        result.append("Path { ");
        result.append("Depth: ").append(depth);
        result.append(", Child [");
        for (int idx=0; idx<depth; ++idx) {
            if (idx > 0) { result.append(", "); }
            result.append(Path.child(path, idx));
        }
        result.append("] }");
        return result.toString();
    }

}
