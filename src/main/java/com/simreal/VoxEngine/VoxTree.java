package com.simreal.VoxEngine;

import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.util.Random;

// QSC powered speakers

public class VoxTree {
    class State {
        public Point3d t0;
        public Point3d t1;
        public Point3d tM;
        public long nodePath;
        public int nodeIndex;
        public int octant;

        State(){
            t0 = new Point3d();
            t1 = new Point3d();
            tM = new Point3d();
            nodePath = 0L;
            nodeIndex = 0;
            octant = 0;
        }

        // TODO: Need to track depth in tree of ray, and path of parents to picked node to recombine the tree on set
        /*public void set(Point3d t0, Point3d t1, Point3d tM, int nodeIndex, int octant) {
            this.t0.set(t0);
            this.t1.set(t1);
            this.tM.set(tM);
            this.nodeIndex = nodeIndex;
            this.octant = octant;
        }*/

        public void set(State state){
            this.t0.set(state.t0);
            this.t1.set(state.t1);
            this.tM.set(state.tM);
            this.nodePath = state.nodePath;
            this.nodeIndex = state.nodeIndex;
            this.octant = state.octant;
        }
    }

    private static int PICK_DEPTH = 256;

    int depth;
    int numNodes;
    int edgeLength;

    long[] nodeArray;
    int firstFreeNode;

    private Point3d nearTopLeft;
    private Point3d farBottomRight;
    private volatile int mirror;

    private Point3d t0;
    private Point3d t1;
    private Point3d origin;
    private Vector3d ray;

    private State[] stateStack;

    private State state;
    private State newState;

    private Random rand;

    public long pickNodePath;
    public int pickNodeIndex;
    public int pickFacet;
    public Vector3d pickRay;


    private int facet;
    private Vector3d facing;

    public static final int XY_PLANE = 1;
    public static final int XZ_PLANE = 2;
    public static final int YZ_PLANE = 4;

    static final int BRICK_EDGE = 16;
    /**
     *
     */
    public VoxTree(int depth){
        this.depth = depth;
        this.edgeLength = (1 << depth) * BRICK_EDGE;
        this.numNodes = 1024 * 1024;

        nodeArray = new long[numNodes];
        firstFreeNode = 0;
        // Chain together all of the free nodes
        for (int idx=0; idx<(numNodes-1); ++idx) {
            nodeArray[idx] = Node.setChild(0L, idx+1);
        }
        nodeArray[numNodes-1] = Node.END_OF_EMPTY;

        nearTopLeft = new Point3d(0, 0, 0);
        farBottomRight = new Point3d(edgeLength, edgeLength, edgeLength);

        t0 = new Point3d();
        t1 = new Point3d();
        origin = new Point3d();
        ray = new Vector3d();

        long nodeIndex =
        nodeArray[0] = Node.setLeaf(0, true);
        firstFreeNode = 1;
        rand = new Random();

        pickNodePath = 0;
        pickNodeIndex = 0;
        pickFacet = 0;
        pickRay = new Vector3d();

        facing = new Vector3d(0.0, 0.0, 0.0);
        facet = 0;

        stateStack = new State[depth+1];
        for (int idx=0; idx<= depth; ++idx) stateStack[idx] = new State();

        state = new State();
        newState = new State();
    }

    public int edgeLength(){
        return edgeLength;
    }

    public int stride(){
        return BRICK_EDGE;
    }


    private long setSubVoxel(int parentIndex, int x0, int y0, int z0, int x1, int y1, int z1, Point3i voxel, long color){
        int xm = (x0 + x1) >> 1;
        int ym = (y0 + y1) >> 1;
        int zm = (z0 + z1) >> 1;

        long parentNode = nodeArray[parentIndex];
        int depth = Node.depth(parentNode);

        // If we've reached the bottom of the tree, set the node and exit
        if (depth == this.depth){
            nodeArray[parentIndex] = Node.setColor(parentNode, color);
            return color;
        }

        // Which child are we in, based on position relative to midpoints;
        // we know we are in the parent voxel already
        int sub = 0;
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
        // TODO: Maintain a free-node list
        int child;
        if (Node.isLeaf(parentNode)){
            long childNode = Node.setDepth(parentNode, (byte) (depth + 1));

            parentNode = Node.setLeaf(parentNode, false);
            parentNode = Node.setChild(parentNode, firstFreeNode);

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
            child = Node.child(parentNode);
        }

        // Descend to children
        switch (sub){
            case 0:
                color = setSubVoxel(child, x0, y0, z0, xm, ym, zm, voxel, color);
                break;
            case 1:
                color = setSubVoxel(child + 1, x0, y0, zm, xm, ym, z1, voxel, color);
                break;
            case 2:
                color = setSubVoxel(child + 2, x0, ym, z0, xm, y1, zm, voxel, color);
                break;
            case 3:
                color = setSubVoxel(child + 3, x0, ym, zm, xm, y1, z1, voxel, color);
                break;
            case 4:
                color = setSubVoxel(child + 4, xm, y0, z0, x1, ym, zm, voxel, color);
                break;
            case 5:
                color = setSubVoxel(child + 5, xm, y0, zm, x1, ym, z1, voxel, color);
                break;
            case 6:
                color = setSubVoxel(child + 6, xm, ym, z0, x1, y1, zm, voxel, color);
                break;
            case 7:
                color = setSubVoxel(child + 7, xm, ym, zm, x1, y1, z1, voxel, color);
                break;
        }

        // If all children are the same color, coalesce into this parent
        boolean merge = true;
        for (int idx=0; idx<8; ++idx){
            long node = nodeArray[child+idx];
            if (color != Node.color(node)) {
                merge = false;
                break;
            }
        }

        if (merge) {

        } else {
            // Accumulate child colors
            long red = 0;
            long green = 0;
            long blue = 0;
            long alpha = 0;
            for (int idx=0; idx<8; ++idx){
                if (idx == sub){
                    red += Color.red(color);
                    green += Color.green(color);
                    blue += Color.blue(color);
                    alpha += Color.alpha(color);
                } else {
                    long node = nodeArray[child+idx];
                    red += Node.red(node);
                    green += Node.green(node);
                    blue += Node.blue(node);
                    alpha += Node.alpha(node);
                }
            }
            nodeArray[parentIndex] = Node.setColor(parentNode, (int) (red >>> 3), (int) (green >>> 3), (int) (blue >>> 3), (int) (alpha >>> 3));
        }

        return Node.color(nodeArray[parentIndex]);
    }



    public void setVoxelPoint(Point3i voxel, int color){
        if ( (voxel.x < nearTopLeft.x)
            || (voxel.y < nearTopLeft.y)
            || (voxel.z < nearTopLeft.z)
            || (voxel.x > farBottomRight.x)
            || (voxel.y > farBottomRight.y)
            || (voxel.z > farBottomRight.z) ){
            return;
        }

        setSubVoxel(0, (int) nearTopLeft.x, (int) nearTopLeft.y, (int) nearTopLeft.z, (int) farBottomRight.x, (int) farBottomRight.y, (int) farBottomRight.z, voxel, color);
    }

    public void setVoxelPath(long path, int color) {
        int nodeIndex = getIndexForPath(path);
        System.out.println("Set " + Path.toString(path) + " (" + nodeIndex + ") to " + Color.toString(color));
        nodeArray[nodeIndex] = Node.setColor(nodeArray[nodeIndex], color);
    }

    public int getIndexForPath(long path) {
        int depth = Path.depth(path);
        int nodeIndex = 0;
        long node;
        for (int cnt=0; cnt<depth; ++cnt) {
            node = nodeArray[nodeIndex];
            nodeIndex = Node.child(node) + Path.child(path, cnt);
        }
        return nodeIndex;
    }

    public Point3i getVoxelPoint(long path) {
        int offset = edgeLength >> 1;
        Point3i center = new Point3i(offset, offset, offset);

        int depth = Path.depth(path);
        int nodeIndex = 0;
        long node;
        for (int cnt=0; cnt<depth; ++cnt) {
            int child = Path.child(path, cnt);

            offset >>= 1;
            if ((child & XY_PLANE) == 0) {
                center.z -= offset;
            } else {
                center.z += offset;
            }
            if ((child & YZ_PLANE) == 0) {
                center.x -= offset;
            } else {
                center.x += offset;
            }
            if ((child & XZ_PLANE) == 0) {
                center.y -= offset;
            } else {
                center.y += offset;
            }

            node = nodeArray[nodeIndex];
            nodeIndex = Node.child(node) + Path.child(path, cnt);
        }
        return center;

    }


    /**
     *
     */
    public long castRay(Point3d inOrigin, Vector3d inRay, boolean pick){
        // Mirror the ray into quadrant 1
        if (pick) {
            pickRay.set(inRay);
        }
        ray.set(inRay);
        origin.set(inOrigin);
        mirror = 0;
        if (ray.x < 0){
            ray.x = -ray.x;
            origin.x = edgeLength - origin.x;
            mirror |= 4;
        }
        if (ray.y < 0){
            ray.y = -ray.y;
            origin.y = edgeLength - origin.y;
            mirror |= 2;
        }
        if (ray.z < 0){
            ray.z = -ray.z;
            origin.z = edgeLength - origin.z;
            mirror |= 1;
        }

        // Find our T values at all six edge planes
        final double verySmallValue = 0.000000001;
        ray.x = Math.max(verySmallValue, ray.x);
        ray.y = Math.max(verySmallValue, ray.y);
        ray.z = Math.max(verySmallValue, ray.z);

        t0.sub(nearTopLeft, origin);
        t0.x /= ray.x;
        t0.y /= ray.y;
        t0.z /= ray.z;

        t1.sub(farBottomRight, origin);
        t1.x /= ray.x;
        t1.y /= ray.y;
        t1.z /= ray.z;

        double tmin = Math.max(t0.x, Math.max(t0.y, t0.z));
        double tmax = Math.min(t1.x, Math.min(t1.y, t1.z));

        long color = 0;
        if ( (tmin < tmax) && (tmax > 0.0d)){
            color = castSubtree(t0, t1, pick);
            if (pick || (Color.alpha(color) >= 250)) return color;
        }
        return Color.blend(color, Color.setColor(rand.nextInt(256), 0, 0, 255));
    }

    /**
     *
     */
    private long castSubtree(Point3d t0, Point3d t1, boolean pick){

        // Error condition early exit
        if ((t1.x < 0.0) || (t1.y < 0.0) || (t1.z < 0.0)) {
            return 0L;
        }

        int stateStackTop = 0;

        Point3d tM1 = new Point3d();
        Point3i tOct = new Point3i();
        long rgba = 0L;

        tM1.add(t0, t1);
        tM1.scale(0.5);
        int octant = findOctant(t0, tM1);
        state.t0 = t0;
        state.t1 = t1;
        state.tM = tM1;
        state.nodePath = 0L;
        state.nodeIndex = 0;
        state.octant = octant;
        stateStack[stateStackTop++].set(state);

        while (stateStackTop > 0){
            // Get the top state from the stack
            state.set(stateStack[--stateStackTop]);

            // Child...
            long node = nodeArray[state.nodeIndex];
            if (Node.isLeaf(node)) {
                // ... value
                long newRgba = Node.color(node);
                if (newRgba > 0) {
                    if (pick) {
                        double tmin = Math.max(state.t0.x, Math.max(state.t0.y, state.t0.z));

                        if (tmin > PICK_DEPTH) {
                            pickNodeIndex = 0;
                        }
                        else{
                            if ( (state.nodeIndex != pickNodeIndex) || (facet != pickFacet) ) {
                                pickNodePath = state.nodePath;
                                pickNodeIndex = state.nodeIndex;
                                pickFacet = facet;
//                                pickRay.set(ray);

                                int index = getIndexForPath(pickNodePath);
                                //System.out.println("Picked " + pickNodeIndex + " -> " + Path.toString(pickNodePath) + " -> " + index);
                            }
                        }

                        return 0;
                    }

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

                    if ((pickNodeIndex > 0) && (pickNodeIndex == state.nodeIndex) && (pickFacet == facet) ){
                        double cycle = (double)System.currentTimeMillis() / 125.0;
                        illumination = 1.5 + (Math.pow(Math.cos(cycle), 3.0) * .5);
                    }
                    rgba = Color.blend(rgba, Color.illuminate(newRgba, illumination));
                    if (Color.alpha(rgba) > 250) return rgba;
                }

            } else {
                int thisOctant = state.octant;
                switch (state.octant){
                    case 0:
                        newState.t0.set(state.t0);
                        newState.t1.set(state.tM);
                        tOct.set(4, 2, 1);
                        break;
                    case 1:
                        newState.t0.set(state.t0.x, state.t0.y, state.tM.z);
                        newState.t1.set(state.tM.x, state.tM.y, state.t1.z);
                        tOct.set(5, 3, 8);
                        break;
                    case 2:
                        newState.t0.set(state.t0.x, state.tM.y, state.t0.z);
                        newState.t1.set(state.tM.x, state.t1.y, state.tM.z);
                        tOct.set(6, 8, 3);
                        break;
                    case 3:
                        newState.t0.set(state.t0.x, state.tM.y, state.tM.z);
                        newState.t1.set(state.tM.x, state.t1.y, state.t1.z);
                        tOct.set(7, 9, 10);
                        break;
                    case 4:
                        newState.t0.set(state.tM.x, state.t0.y, state.t0.z);
                        newState.t1.set(state.t1.x, state.tM.y, state.tM.z);
                        tOct.set(8, 6, 5);
                        break;
                    case 5:
                        newState.t0.set(state.tM.x, state.t0.y, state.tM.z);
                        newState.t1.set(state.t1.x, state.tM.y, state.t1.z);
                        tOct.set(9, 7, 12);
                        break;
                    case 6:
                        newState.t0.set(state.tM.x, state.tM.y, state.t0.z);
                        newState.t1.set(state.t1.x, state.t1.y, state.tM.z);
                        tOct.set(10, 12, 7);
                        break;
                    case 7:
                        newState.t0.set(state.tM);
                        newState.t1.set(state.t1);
                        tOct.set(11, 13, 14);
                        break;
                }

                // Traverse
                state.octant = nextOctant(newState.t1, tOct);
                if (state.octant < 8) {
                    stateStack[stateStackTop++].set(state);
                }

                // ... descend
                newState.tM.add(newState.t0, newState.t1);
                newState.tM.scale(0.5);
                octant = findOctant(newState.t0, newState.tM);
                newState.octant = octant;
                int octantMirror = thisOctant ^ mirror;
                newState.nodeIndex = Node.child(node) + octantMirror;
                newState.nodePath = Path.addChild(state.nodePath, octantMirror);
                stateStack[stateStackTop++].set(newState);
            }
        }
        return rgba;
    }

    /**
     *
     *
     */
    private int findOctant(Point3d t0, Point3d tM){
        int octant = 0;

        if (t0.x > t0.y){
            if (t0.x > t0.z){ // enter YZ Plane
                if (t0.x > tM.y) octant |= 2;
                if (t0.x > tM.z) octant |= 1;
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;

                return octant;
            }
        }
        else{
            if (t0.y > t0.z){ // enter XZ Plane
                if (t0.y > tM.x) octant |= 4;
                if (t0.y > tM.z) octant |= 1;
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;

                return octant;
            }
        }
        // enter XY Plane
        if (t0.z > tM.x) octant |= 4;
        if (t0.z > tM.y) octant |= 2;
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;

        return octant;
    }

    /**
     *
     */
    private int nextOctant(Point3d t1, Point3i octant){
        if (t1.x < t1.y){
            if (t1.x < t1.z){
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;
                return octant.x;    // exit YZ Plane
            }
        }
        else{
            if (t1.y < t1.z){
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;
                return octant.y;     // exit XZ Plane
            }
        }
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;
        return octant.z;  // exit XY Plane
    }

    class VoxTreeStatistics {
        public int depth;
        public int numNodes;
        public int numLeaves;

        VoxTreeStatistics() {
            depth = 0;
            numNodes = 0;
            numLeaves = 0;
        }
    }

    VoxTreeStatistics analyze() {
        VoxTreeStatistics stats = new VoxTreeStatistics();

        for (int idx=0; idx<firstFreeNode; ++idx){
            if (Node.isLeaf(nodeArray[idx]))
                ++stats.numLeaves;
            else
                ++stats.numNodes;
        }

        stats.depth = this.depth;

        return stats;
    }

    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        int nodeCnt = 0;
        int leafCnt = 0;
        for (int idx=0; idx<firstFreeNode; ++idx){
            if (Node.isLeaf(nodeArray[idx]))
                ++leafCnt;
            else
                ++nodeCnt;
        }
        result.append(this.getClass()).append(" Object {").append(NEW_LINE);
        result.append("   Free Node: ").append(firstFreeNode).append(" of ").append(this.numNodes).append(NEW_LINE);
        result.append("   (").append(nodeCnt).append(" nodes, ").append(leafCnt).append(" leaves)").append(NEW_LINE);
        result.append("   Tree Depth: ").append(this.depth).append(NEW_LINE);
        result.append("   Edge Length: ").append(edgeLength).append(NEW_LINE);
        result.append("   Corner 0: ").append(nearTopLeft).append(NEW_LINE);
        result.append("   Corner 1: ").append(farBottomRight).append(NEW_LINE);
        for (int idx=0; idx<Math.min(65, firstFreeNode); ++idx){
            result.append(idx);
            result.append(": ");
            result.append(Node.toString(nodeArray[idx]));
            result.append(NEW_LINE);
        }
        if (firstFreeNode > 65) result.append("...").append(NEW_LINE);
        result.append("}");

        return result.toString();
    }
}
