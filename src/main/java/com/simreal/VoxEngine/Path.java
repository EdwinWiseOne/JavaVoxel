package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;
import javax.vecmath.Point3i;

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
     *    | child[16] : 3-bits * 19 = 57                          |
     *    +------+------+------+------+------+------+------+------+
     *    |                                          |n/a | depth |
     *    +------+------+------+------+------+------+------+------+
     *                24            16             8 7    5      0
     */
    // TODO: Create a path iterator

    public static final int PATH_MAX_DEPTH     = 19;

    static final long PATH_DEPTH_MASK   = 0x000000000000001FL;
    static final long PATH_PATH_MASK    = 0xFFFFFFFFFFFFFF80L;
    static final long PATH_HEAD_MASK    = 0xE000000000000000L;

    static final long PATH_CHILD_MASK   = 0x0000000000000007L;

    static final byte PATH_DEPTH_SHIFT  = 0;
    static final byte PATH_PATH_SHIFT   = 7;
    static final byte PATH_HEAD_SHIFT   = 61;


    public static final int Z_AXIS = 1;
    public static final int Y_AXIS = 2;
    public static final int X_AXIS = 4;


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

    /**
     * Given a position (within the given volume) determine the path to that position
     * (to a given depth)
     *
     * @param position
     * @param edgeLength
     * @param depth
     * @return
     */
    public static long fromPosition(Point3i position, int edgeLength, int depth) {
        if ( (position.x < 0)
                || (position.y < 0)
                || (position.z < 0)
                || (position.x > edgeLength)
                || (position.y > edgeLength)
                || (position.z > edgeLength) ){
            return 0L;
        }

        int x0 = 0;
        int y0 = 0;
        int z0 = 0;
        int x1 = edgeLength;
        int y1 = edgeLength;
        int z1 = edgeLength;

        int xm;
        int ym;
        int zm ;

        long path = 0L;
        int sub;

        for (int level=0; level<depth; ++level) {
            xm = (x0 + x1) >> 1;
            ym = (y0 + y1) >> 1;
            zm = (z0 + z1) >> 1;

            // Which sub-voxel are we in, based on position relative to midpoints;
            // we know we are in the parent voxel already
            sub = 0;
            if (position.z > zm){
                sub |= Z_AXIS;  // 1
            }
            if (position.y > ym){
                sub |= Y_AXIS;  // 2
            }
            if (position.x > xm){
                sub |= X_AXIS;  // 4
            }
            path = Path.addChild(path, sub);

            // Reconfigure to the sub-voxel
            switch (sub){
                case 0:
                    z1 = zm;
                    y1 = ym;
                    x1 = xm;
                    break;
                case 1:
                    z0 = zm;
                    y1 = ym;
                    x1 = xm;
                    break;
                case 2:
                    z1 = zm;
                    y0 = ym;
                    x1 = xm;
                    break;
                case 3:
                    z0 = zm;
                    y0 = ym;
                    x1 = xm;
                    break;
                case 4:
                    z1 = zm;
                    y1 = ym;
                    x0 = xm;
                    break;
                case 5:
                    z0 = zm;
                    y1 = ym;
                    x0 = xm;
                    break;
                case 6:
                    z1 = zm;
                    y0 = ym;
                    x0 = xm;
                    break;
                case 7:
                    z0 = zm;
                    y0 = ym;
                    x0 = xm;
                    break;
            }
        }

        return path;
    }


    /**
     *  Parse a path, which is a series of child choices that represent a descent down an oct-tree,
     *  into a position in cube space (the minimum corner)
     *
     * @param path
     * @param edgeLength
     * @return
     */
    public static Point3i toPosition(long path, int edgeLength) {
        int x0 = 0;
        int y0 = 0;
        int z0 = 0;
        int x1 = edgeLength;
        int y1 = edgeLength;
        int z1 = edgeLength;

        int xm=0;
        int ym=0;
        int zm=0;

        int depth = Path.depth(path);
        for (int cnt=0; cnt<depth; ++cnt) {
            xm = (x0 + x1) >> 1;
            ym = (y0 + y1) >> 1;
            zm = (z0 + z1) >> 1;

            switch (Path.child(path, cnt)){
                case 0:
                    z1 = zm;
                    y1 = ym;
                    x1 = xm;
                    break;
                case 1:
                    z0 = zm;
                    y1 = ym;
                    x1 = xm;
                    break;
                case 2:
                    z1 = zm;
                    y0 = ym;
                    x1 = xm;
                    break;
                case 3:
                    z0 = zm;
                    y0 = ym;
                    x1 = xm;
                    break;
                case 4:
                    z1 = zm;
                    y1 = ym;
                    x0 = xm;
                    break;
                case 5:
                    z0 = zm;
                    y1 = ym;
                    x0 = xm;
                    break;
                case 6:
                    z1 = zm;
                    y0 = ym;
                    x0 = xm;
                    break;
                case 7:
                    z0 = zm;
                    y0 = ym;
                    x0 = xm;
                    break;
            }
        }
        // Enforce position to middle of voxel
        xm = (x0 + x1) >> 1;
        ym = (y0 + y1) >> 1;
        zm = (z0 + z1) >> 1;

        return new Point3i(xm, ym, zm);
    }

    /**
     * Parse a path into an ID sequence, by intermixing the bits.  This should turn the X, Y, Z into
     * an ID long that still preserves locality.
     *
     * @param path
     * @return
     */
    public static long toID(long path) {
        return 0L;
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
