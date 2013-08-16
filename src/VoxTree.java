import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.util.Formatter;
import java.util.Random;

public class VoxTree {
    private static final int TREE_NODE_COUNT = 1 << 18;
    private static final int TREE_DEPTH = 8;
    private static final int edgeLength = (1 << TREE_DEPTH) * 16;

    private long[] nodeArray;
    private int firstFreeNode = 0;

    private Point3i nearTopLeft;
    private Point3i farBottomRight;
    private int mirror;

    private Random rand;

    private int hotNode;

    private Vector3d facing;
    private int facet;

    private static final int XY_PLANE = 1;
    private static final int YZ_PLANE = 4;
    private static final int XZ_PLANE = 2;

    /*
     * 64 bit node entries:
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
    private static final byte NODE_FLAG_LEAF_SHIFT  = 56;
    private static final byte NODE_DEPTH_SHIFT      = 60;

    private static long setNodeColor(long node, int red, int green, int blue, int alpha){
        long rgba = ((long)red << NODE_RED_SHIFT)
                | ((long)green << NODE_GREEN_SHIFT)
                | ((long)blue << NODE_BLUE_SHIFT)
                | ((long)alpha << NODE_ALPHA_SHIFT);
        return (node & ~NODE_RGBA_MASK) | rgba;
    }
    private static long setNodeColor(long node, long rgba){
        return (node & ~NODE_RGBA_MASK) | (((long)rgba & 0xFFFFFFFFL) << NODE_RGBA_SHIFT);
    }

    private static int getNodeRed(long node){
        return (int)((node & NODE_RED_MASK) >>> NODE_RED_SHIFT);
    }
    private static int getNodeGreen(long node){
        return (int)((node & NODE_GREEN_MASK) >>> NODE_GREEN_SHIFT);
    }
    private static int getNodeBlue(long node){
        return (int)((node & NODE_BLUE_MASK) >>> NODE_BLUE_SHIFT);
    }
    private static int getNodeAlpha(long node){
        return (int)((node & NODE_ALPHA_MASK) >>> NODE_ALPHA_SHIFT);
    }
    private static long getNodeColor(long node){
        return (node & NODE_RGBA_MASK) >>> NODE_RGBA_SHIFT;
    }
    private static int getColorRed(long color){
        return (int)((color & NODE_RED_MASK) >>> NODE_RED_SHIFT);
    }
    private static int getColorGreen(long color){
        return (int)((color & NODE_GREEN_MASK) >>> NODE_GREEN_SHIFT);
    }
    private static int getColorBlue(long color){
        return (int)((color & NODE_BLUE_MASK) >>> NODE_BLUE_SHIFT);
    }
    private static int getColorAlpha(long color){
        return (int)((color & NODE_ALPHA_MASK) >>> NODE_ALPHA_SHIFT);
    }
    public static long setColor(int red, int green, int blue, int alpha){
        return ((long)red << NODE_RED_SHIFT)
                | ((long)green << NODE_GREEN_SHIFT)
                | ((long)blue << NODE_BLUE_SHIFT)
                | ((long)alpha << NODE_ALPHA_SHIFT);
    }
    public static long illuminateColor(long color, double illum){
        int red = getColorRed(color);
        int green = getColorGreen(color);
        int blue = getColorBlue(color);
        int alpha = getColorAlpha(color);

        red = (int)(red * illum);
        green = (int)(green * illum);
        blue = (int)(blue * illum);
        return setColor(red, green, blue, alpha);
    }

    /**
     *
     * @param c1 First color to blend
     * @param c2 Second color to blend
     * @return Final blended color
     */
    private static long blendColors(long c1, long c2){
        double a1 = (double)getColorAlpha(c1) / 255.0;
        double a2 = (double)getColorAlpha(c2) / 255.0;
        double a3 = a1 + a2*(1.0-a1);

        long red = (long)(((double)getColorRed(c1)*a1 + (double)getColorRed(c2)*a2*(1.0-a1)) / a3);
        long green = (long)(((double)getColorGreen(c1)*a1 + (double)getColorGreen(c2)*a2*(1.0-a1)) / a3);
        long blue = (long)(((double)getColorBlue(c1)*a1 + (double)getColorBlue(c2)*a2*(1.0-a1)) / a3);
        long alpha = (long)(a3 * 255.0);

        return (red << NODE_RED_SHIFT)
                | (green << NODE_GREEN_SHIFT)
                | (blue << NODE_BLUE_SHIFT)
                | (alpha << NODE_ALPHA_SHIFT);
    }

    private static long setNodeChild(long node, int child){
        return (node & ~NODE_CHILD_MASK)
                | ((long)child << NODE_CHILD_SHIFT);
    }
    private static int getNodeChild(long node){
        return (int)((node & NODE_CHILD_MASK) >>> NODE_CHILD_SHIFT);
    }

    private static long setNodeLeaf(long node, boolean leaf){
        if (leaf){
            return (node | NODE_FLAG_LEAF_MASK);
        }
        return (node & ~NODE_FLAG_LEAF_MASK);
    }
    private static boolean isNodeLeaf(long node){
        return (node & NODE_FLAG_LEAF_MASK) == NODE_FLAG_LEAF_MASK;
    }

    private static long setNodeDepth(long node, byte depth){
        return (node & ~NODE_DEPTH_MASK)
                | ((long)depth << NODE_DEPTH_SHIFT);
    }
    private static byte getNodeDepth(long node){
        return (byte)((node & NODE_DEPTH_MASK) >>> NODE_DEPTH_SHIFT);
    }

    private String nodeToString(long node){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();
        String NEW_LINE = System.getProperty("line.separator");

        if (isNodeLeaf(node)) {
            result.append("LEAF {");
        } else {
            result.append("NODE {");
        }
        fmt.format("%08X", getNodeColor(node));
        result.append("   Color: " + fmt.toString());
        result.append("   Depth: " + getNodeDepth(node));
        result.append("   Child: " + getNodeChild(node));
        result.append("}" + NEW_LINE);
        return result.toString();
    }
    /**
     *
     */
    public VoxTree(){
        nodeArray = new long[TREE_NODE_COUNT];

        nearTopLeft = new Point3i(0, 0, 0);
        farBottomRight = new Point3i(edgeLength, edgeLength, edgeLength);

        nodeArray[0] = setNodeLeaf(0, true);
        firstFreeNode = 1;
        rand = new Random();

        hotNode = 0;
        facing = new Vector3d(0.0, 0.0, 0.0);
        facet = 0;
    }

    public int setHotNode(int node){
        int lastNode = hotNode;
        hotNode = node;
        return lastNode;
    }

    /**
     *
     * @param parent
     * @param voxel
     * @param color
     * @return Color of the leaf / average color of the node
     */
    private long setSubVoxel(int parent, int x0, int y0, int z0, int x1, int y1, int z1, Point3i voxel, long color){
        int xm = (x0 + x1) >> 1;
        int ym = (y0 + y1) >> 1;
        int zm = (z0 + z1) >> 1;

        long parentNode = nodeArray[parent];
        int depth = getNodeDepth(parentNode);

        if (depth == TREE_DEPTH){
            nodeArray[parent] = setNodeColor(parentNode, color);
            return color;
        }

        // Which child are we in, based on position relative to midpoints;
        // we know we are in the parent voxel already
        int sub = 0;
        int child = 0;
        if (voxel.z > zm){
            sub |= 1;
        }
        if (voxel.y > ym){
            sub |= 2;
        }
        if (voxel.x > xm){
            sub |= 4;
        }

        // Break leaf into a node
        if (isNodeLeaf(parentNode)){
            long childNode = setNodeDepth(parentNode, (byte)(depth+1));
            //childNode = setNodeColor(childNode, 0x00FF00FF);

            parentNode = setNodeLeaf(parentNode, false);
            parentNode = setNodeChild(parentNode, firstFreeNode);

            // TODO: Don't double-set our actual child
            child = firstFreeNode;
            nodeArray[firstFreeNode] = childNode;
            nodeArray[firstFreeNode+1] = childNode;
            nodeArray[firstFreeNode+2] = childNode;
            nodeArray[firstFreeNode+3] = childNode;
            nodeArray[firstFreeNode+4] = childNode;
            nodeArray[firstFreeNode+5] = childNode;
            nodeArray[firstFreeNode+6] = childNode;
            nodeArray[firstFreeNode+7] = childNode;

            firstFreeNode += 8; // TODO: BOUNDS CHECKING!
        } else {
            child = getNodeChild(parentNode);
        }

        // Descend to children
        switch (sub){
            case 0:
                color = setSubVoxel(child, x0, y0, z0, xm, ym, zm, voxel, color );
                break;
            case 1:
                color = setSubVoxel(child+1, x0, y0, zm, xm, ym, z1, voxel, color );
                break;
            case 2:
                color = setSubVoxel(child+2, x0, ym, z0, xm, y1, zm, voxel, color );
                break;
            case 3:
                color = setSubVoxel(child+3, x0, ym, zm, xm, y1, z1, voxel, color );
                break;
            case 4:
                color = setSubVoxel(child+4, xm, y0, z0, x1, ym, zm, voxel, color );
                break;
            case 5:
                color = setSubVoxel(child+5, xm, y0, zm, x1, ym, z1, voxel, color );
                break;
            case 6:
                color = setSubVoxel(child+6, xm, ym, z0, x1, y1, zm, voxel, color );
                break;
            case 7:
                color = setSubVoxel(child+7, xm, ym, zm, x1, y1, z1, voxel, color );
                break;
        }

        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        for (int idx=0; idx<8; ++idx){
            if (idx == sub){
                red += getColorRed(color);
                green += getColorGreen(color);
                blue += getColorBlue(color);
                alpha += getColorAlpha(color);
            } else {
                long node = nodeArray[child+idx];
                red += getNodeRed(node);
                green += getNodeGreen(node);
                blue += getNodeBlue(node);
                alpha += getNodeAlpha(node);
            }
        }

        nodeArray[parent] = setNodeColor(parentNode, (int)(red >>>3), (int)(green >>> 3), (int)(blue >>> 3), (int)(alpha >>> 3));

        return getNodeColor(nodeArray[parent]);
    }

    public void setVoxel(Point3i voxel, int color){
        if ( (voxel.x < nearTopLeft.x)
            || (voxel.y < nearTopLeft.y)
            || (voxel.z < nearTopLeft.z)
            || (voxel.x > farBottomRight.x)
            || (voxel.y > farBottomRight.y)
            || (voxel.z > farBottomRight.z) ){
            return;
        }

        setSubVoxel(0, nearTopLeft.x, nearTopLeft.y, nearTopLeft.z, farBottomRight.x, farBottomRight.y, farBottomRight.z, voxel, color);
    }
    /**
     *
     */
    private int findFirstNode(double tx0, double ty0, double tz0, double txM, double tyM, double tzM){
        int firstNode = 0;

        if (tx0 > ty0){
            if (tx0 > tz0){ // enter YZ Plane
                if (tx0 > tyM) firstNode |= 2;
                if (tx0 > tzM) firstNode |= 1;
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;

                return firstNode;
            }
        }
        else{
            if (ty0 > tz0){ // enter XZ Plane
                if (ty0 > txM) firstNode |= 4;
                if (ty0 > tzM) firstNode |= 1;
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;

                return firstNode;
            }
        }
        // enter XY Plane
        if (tz0 > txM) firstNode |= 4;
        if (tz0 > tyM) firstNode |= 2;
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;

        return firstNode;
    }


    /**
     *
     */
    private int nextNode(double tx, double ty, double tz, int cx, int cy, int cz){
        if (tx < ty){
            if (tx < tz){
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;
                return cx;    // exit YZ Plane
            }
        }
        else{
            if (ty < tz){
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;
                return cy;     // exit XZ Plane
            }
        }
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;
        return cz;  // exit XY Plane
    }

    /**
     *
     */
    private long processSubtree(
            double tx0,
            double ty0,
            double tz0,
            double tx1,
            double ty1,
            double tz1,
            int nodeIndex,
            long rgba,
            boolean pick)
    {
        long node = nodeArray[nodeIndex];
        int childIndex = getNodeChild(node);
        int cube;

        // Error condition?
        if ((tx1 < 0.0) || (ty1 < 0.0) || (tz1 < 0.0)) {
            return rgba;
        }

        // Early exit if all filled up
        if (pick && rgba > 0) return rgba;
        else if (getColorAlpha(rgba) > 250) return rgba;


        // Leaf condition, no further subdivision
        if (isNodeLeaf(node)){
            rgba = getNodeColor(node);
            if ( pick && (getColorAlpha(rgba) > 10) ){
                return nodeIndex;
            }

            if ((hotNode > 0) && (hotNode == nodeIndex)) return 0xFFFFFFFF;

            // Lighting model!
            // Fake it for now, no lights yet
            Vector3d diffuseLight;
            double diffuseCoefficient = 0.4;
            double ambientCoefficient = 0.5;
            // TODO: Specular, distance attenuation, atmospheric effect, etc

            double elevation = Math.toRadians(-10);
            double heading = Math.toRadians(45);
            double cosElevation = Math.cos(elevation);
            diffuseLight = new Vector3d(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);

            Vector3d normal = new Vector3d(facing);
            if ((facet & mirror) > 0)
                normal.scale(-1);
            double illumination = ambientCoefficient + diffuseCoefficient*diffuseLight.dot(normal);

            return illuminateColor(rgba, illumination);
        }

        // TODO: LOD exit condition

        // Midpoint of this node
        double txM = 0.5 * (tx0 + tx1);
        double tyM = 0.5 * (ty0 + ty1);
        double tzM = 0.5 * (tz0 + tz1);

        // Root sub-cube
        cube = findFirstNode(tx0, ty0, tz0, txM, tyM, tzM);

        // Traverse
        long nextColor = 0;
        do {
            switch (cube){
                case 0:
                    nextColor = processSubtree(tx0, ty0, tz0, txM, tyM, tzM, childIndex + (0 ^ mirror), rgba, pick);
                    cube = nextNode(txM, tyM, tzM, 4, 2, 1);
                    break;
                case 1:
                    nextColor = processSubtree(tx0, ty0, tzM, txM, tyM, tz1, childIndex + (1 ^ mirror), rgba, pick);
                    cube = nextNode(txM, tyM, tz1, 5, 3, 8);
                    break;
                case 2:
                    nextColor = processSubtree(tx0, tyM, tz0, txM, ty1, tzM, childIndex + (2 ^ mirror), rgba, pick);
                    cube = nextNode(txM, ty1, tzM, 6, 8, 3);
                    break;
                case 3:
                    nextColor = processSubtree(tx0, tyM, tzM, txM, ty1, tz1, childIndex + (3 ^ mirror), rgba, pick);
                    cube = nextNode(txM, ty1, tz1, 7, 8, 8);
                    break;
                case 4:
                    nextColor = processSubtree(txM, ty0, tz0, tx1, tyM, tzM, childIndex + (4 ^ mirror), rgba, pick);
                    cube = nextNode(tx1, tyM, tzM, 8, 6, 5);
                    break;
                case 5:
                    nextColor = processSubtree(txM, ty0, tzM, tx1, tyM, tz1, childIndex + (5 ^ mirror), rgba, pick);
                    cube = nextNode(tx1, tyM, tz1, 8, 7, 8);
                    break;
                case 6:
                    nextColor = processSubtree(txM, tyM, tz0, tx1, ty1, tzM, childIndex + (6 ^ mirror), rgba, pick);
                    cube = nextNode(tx1, ty1, tzM, 8,8,7);
                    break;
                case 7:
                    nextColor = processSubtree(txM, tyM, tzM, tx1, ty1, tz1, childIndex + (7 ^ mirror), rgba, pick);
                    cube = 8;
                    break;
            }

            if (pick) rgba = nextColor;
            else rgba = blendColors(rgba, nextColor);

        } while (cube < 8);

        return rgba;
    }

    /**
     *
     */
    public long walkRay(int ox, int oy, int oz, double dx, double dy, double dz, boolean pick){
        // Mirror the ray into quadrant 1
        mirror = 0;
        if (dx < 0){
            dx = -dx;
            ox = edgeLength - ox;
            mirror |= 4;
        }
        if (dy < 0){
            dy = -dy;
            oy = edgeLength - oy;
            mirror |= 2;
        }
        if (dz < 0){
            dz = -dz;
            oz = edgeLength - oz;
            mirror |= 1;
        }

        // Find our T values at all six edge planes
        final double verySmallValue = 0.000000001;
        dx = 1.0 / Math.max(verySmallValue, dx);
        dy = 1.0 / Math.max(verySmallValue, dy);
        dz = 1.0 / Math.max(verySmallValue, dz);


        double tx0 = (nearTopLeft.x - ox) * (double)dx;
        double ty0 = (nearTopLeft.y - oy) * (double)dy;
        double tz0 = (nearTopLeft.z - oz) * (double)dz;

        double tx1 = (farBottomRight.x - ox) * (double)dx;
        double ty1 = (farBottomRight.y - oy) * (double)dy;
        double tz1 = (farBottomRight.z - oz) * (double)dz;

        double tmin = Math.max(tx0, Math.max(ty0, tz0));
        double tmax = Math.min(tx1, Math.min(ty1, tz1));

        long color = 0;
        if ( (tmin < tmax) && (tmax > 0.0d)){
            color = processSubtree(tx0, ty0, tz0, tx1, ty1, tz1, 0, 0, pick);
        }
        if (pick || (getColorAlpha(color) >= 250)) return color;
        return blendColors(color, setColor(rand.nextInt(256), 0, 0, 255));
    }

    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass() + " Object {" + NEW_LINE);
        result.append("   Free Node: " + firstFreeNode + NEW_LINE);
        result.append("   Tree Depth: " + TREE_DEPTH + NEW_LINE);
        result.append("   Edge Length: " + edgeLength + NEW_LINE);
        result.append("   Corner 0: " + nearTopLeft + NEW_LINE);
        result.append("   Corner 1: " + farBottomRight + NEW_LINE);
        for (int idx=0; idx<Math.min(65, firstFreeNode); ++idx){
            result.append(idx);
            result.append(": ");
            result.append(nodeToString(nodeArray[idx]));
        }
        result.append("}");

        return result.toString();
    }
}
