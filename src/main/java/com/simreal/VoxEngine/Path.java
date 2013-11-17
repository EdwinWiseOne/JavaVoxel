package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.vecmath.Point3i;

import java.util.Formatter;

/**
 * A Path is the series of branching decisions taken while traversing the octal
 * tree, and the path uniquely identifies a given voxel in the tree. The length
 * of the path determines how var down the tree the voxel it identifies lives.
 *
 * This class defines all the relevant bit manipulations for the defining and
 * querying a path, as stored in a simple long primitive value.
 *
 * A path is composed of two parts - the 3-bit child choices form the root
 * of the tree to the given node, and the tree length of that node.
 * Only the first n=length child selections count in the path, and
 * if the given length is less than the tree length, it represents a parent
 * node and not a leaf.
 *
 * A Path is atomic to 64-bit operations.
 *
 * <pre>
 *  64              56              48              40              32
 *    +-------+-------+-------+-------+-------+-------+-------+-------+
 *    | child[16] : 3-bits * 19 = 57                                  |
 *    +-------+-------+-------+-------+-------+-------+-+---+-+-------+
 *    |                                                 |n/a| length  |
 *    +-------+-------+-------+-------+-------+-------+-+---+-+-------+
 *  32              24              16               8 7 6 5 4       0
 *
 * </pre>
 */
@Stateless
public class Path {

    static final Logger LOG = LoggerFactory.getLogger(Path.class.getName());

    /** Maximum path length */
    public static final int PATH_MAX_LENGTH = 19;

    static final long PATH_LENGTH_MASK  = 0x000000000000001FL;
    static final long PATH_PATH_MASK    = 0xFFFFFFFFFFFFFF80L;
    static final long PATH_HEAD_MASK    = 0xE000000000000000L;

    static final long PATH_CHILD_MASK   = 0x0000000000000007L;

    static final byte PATH_LENGTH_SHIFT = 0;
    static final byte PATH_PATH_SHIFT   = 7;
    static final byte PATH_HEAD_SHIFT   = 61;

    /** Child choice along the Z axis (0 near, 1, far) */
    public static final int Z_AXIS = 1;
    /** Child choice along the Y axis (0 near, 2 far) */
    public static final int Y_AXIS = 2;
    /** Child choice along the X axis (0 near, 4 far) */
    public static final int X_AXIS = 4;


    /**
     * Returns the length of the path (the depth of the node it represents in the tree)
     *
     * @param path      Path long to interpret
     * @return          Length of the path
     */
    public static int length(long path){
        int ret = (int)((path & PATH_LENGTH_MASK) >>> PATH_LENGTH_SHIFT);
        return ret;
    }

    /**
     * Returns the child-choice entry in the path at the given position in the path
     *
     * @param path      Path long to interpret
     * @param position  Position of the child choice to return
     * @return          Child choice at the given position of the path
     */
    public static int child(long path, int position) {
        int offset = 3*position;
        long mask = PATH_HEAD_MASK >>> offset;
        long shift = PATH_HEAD_SHIFT - offset;

        int ret = (int)((path & mask) >>> shift);
        return ret;
    }

    /**
     * Sets the child-choice entry in the path at the given position in the path
     *
     * @param path      Path long to adjust
     * @param position  Position of the child choice to set
     * @param child     Child choice to set in the path
     * @return          The path as changed by the new child choice
     */
    private static long setChild(long path, int position, int child) {
        int offset = 3*position;
        long mask = PATH_HEAD_MASK >>> offset;
        long shift = PATH_HEAD_SHIFT - offset;

        return (path & ~mask) | ((child & PATH_CHILD_MASK)  << shift);
    }

    /**
     * Appends a new child choice to the end of the path, incrementing the
     * path length to match.
     *
     * If this additional choice would exceed the maximum path length, the
     * path is returned unchanged.
     *
     * @param path      Path long to adjust
     * @param child     Child choice to append to the path
     * @return          The path as changed by the new child choice
     */
    public static long addChild(long path, int child) {
        int length = Path.length(path);
        if (length >= PATH_MAX_LENGTH) return path;

        return setLength(setChild(path, length, child), length + 1);
    }

    /**
     * Sets the length parameter in the path, which controls how many path
     * choices are relevant in the path. Can be used, for example, to do a partial
     * path traversal by reducing the path length from its true maximum.
     *
     * @param path      Path long to adjust
     * @param length    New length to set in the path
     * @return          The path as changed by the new length
     */
    public static long setLength(long path, int length) {
        return (path & ~PATH_LENGTH_MASK) | (length << PATH_LENGTH_SHIFT);
    }

    /**
     * Calculates the path to a given 3D position within the voxel tree. Calculates
     * to the given depth in the tree only.  If the position is outside of the tree,
     * returns 0L (which is the root, but not a useful path).
     *
     * Tree organization, as defined by child choices subdividing the voxel.
     *
     * <pre>
     * Y axis, bit value 2
     * ^     +----------+----------+
     * |    /    3     /     7    /|
     * |   /          /          / |
     * |  +----------+----------+  |
     * | /    2     /     6    /|7 |
     * |/          /          / |  +
     * +----------+----------+  | /|
     * |          |          |6 |/ |
     * |          |          |  +  |
     * |    2     |     6    | /|5 |
     * |          |          |/ |  +
     * +----------+----------+  | /
     * |          |          |4 |/
     * |          |          |  +
     * |    0     |     4    | /
     * |          |          |/
     * +----------+----------+--------> X axis, bit value 4
     *
     * The hidden child voxel is '1'; Z axis points into the reading plane, bit value 1
     * </pre>
     *
     * @param position      3D position that is hopefully within the vox tree's extents
     * @param edgeLength    Voxel Tree's edge length, defining the dimensions of the root voxel
     * @param depth         How deep into the tree the path travels (length of the path)
     * @return              Path into the tree
     */
    public static long fromPosition(Point3i position, int edgeLength, int depth) {

        // --------------------------------------
        // Check position against normalized tree extents.
        // --------------------------------------
        if ( (position.x < 0)
                || (position.y < 0)
                || (position.z < 0)
                || (position.x > edgeLength)
                || (position.y > edgeLength)
                || (position.z > edgeLength) ){
            return 0L;
        }

        // --------------------------------------
        // Starting extents for the root node, which is the full extent of the vox tree
        // --------------------------------------
        int x0 = 0;
        int y0 = 0;
        int z0 = 0;
        int x1 = edgeLength;
        int y1 = edgeLength;
        int z1 = edgeLength;

        // Midpoints for descent subdivision
        int xm;
        int ym;
        int zm ;

        // --------------------------------------
        // Path in progress and sub voxel child choice
        // --------------------------------------
        long path = 0L;
        int child;

        // --------------------------------------
        // For each level in the tree, choose a sub voxel child
        // --------------------------------------
        for (int level=0; level<depth; ++level) {

            // --------------------------------------
            // Midpoint of this voxel defines a corner of the child voxel
            // --------------------------------------
            xm = (x0 + x1) >> 1;
            ym = (y0 + y1) >> 1;
            zm = (z0 + z1) >> 1;

            // --------------------------------------
            // Sub-voxel are we in, based on position relative to the midpoint
            // 8 possible choices, 0..7
            // --------------------------------------
            child = 0;
            if (position.z > zm){
                child |= Z_AXIS;  // 1
            }
            if (position.y > ym){
                child |= Y_AXIS;  // 2
            }
            if (position.x > xm){
                child |= X_AXIS;  // 4
            }

            // --------------------------------------
            // Update path in progress
            // --------------------------------------
            path = Path.addChild(path, child);

            // --------------------------------------
            // Set the voxel extents to the chosen child voxel
            // --------------------------------------
            switch (child){
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
     * Calculates the midpoint coordinate of the tree voxel that the path leads to, in voxel tree space.
     *
     * @param path          Path through the voxel tree
     * @param edgeLength    Voxel Tree's edge length, defining the dimensions of the root voxel
     * @return              Coordinate of the midpoint of the voxel
     */
    public static Point3i toPosition(long path, int edgeLength) {
        // --------------------------------------
        // Starting extents for the root node, which is the full extent of the vox tree
        // --------------------------------------
        int x0 = 0;
        int y0 = 0;
        int z0 = 0;
        int x1 = edgeLength;
        int y1 = edgeLength;
        int z1 = edgeLength;

        // Midpoints for descent subdivision
        int xm=0;
        int ym=0;
        int zm=0;

        // --------------------------------------
        // Traverse the path, defining child voxels as we go
        // --------------------------------------
        int depth = Path.length(path);
        for (int cnt=0; cnt<depth; ++cnt) {
            // --------------------------------------
            // Midpoint of this voxel defines a corner of the child voxel
            // --------------------------------------
            xm = (x0 + x1) >> 1;
            ym = (y0 + y1) >> 1;
            zm = (z0 + z1) >> 1;

            // --------------------------------------
            // Process the path choice
            // --------------------------------------
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

        // --------------------------------------
        // Final midpoint for the return coordinate
        // --------------------------------------
        xm = (x0 + x1) >> 1;
        ym = (y0 + y1) >> 1;
        zm = (z0 + z1) >> 1;

        return new Point3i(xm, ym, zm);
    }

    /**
     * Generate a string representation of the path, for debugging purposes.
     *
     * @param path  Path long to interpret
     * @return      String representation of the path
     */
    static public String toString(long path){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();
        String NEW_LINE = System.getProperty("line.separator");

        int depth = Path.length(path);
        result.append("Path { ");
        result.append("Depth: ").append(depth);
        result.append(", [");
        for (int idx=0; idx<depth; ++idx) {
            if (idx > 0) { result.append(", "); }
            result.append(Path.child(path, idx));
        }
        result.append("] }");
        return result.toString();
    }

}
